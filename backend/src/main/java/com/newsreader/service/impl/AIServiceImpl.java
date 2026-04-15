package com.newsreader.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsreader.service.AIService;
import com.newsreader.service.SystemConfigService;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class AIServiceImpl implements AIService {
    private static final int SUMMARY_TOKENS = 220;
    private static final int KEYWORD_TOKENS = 120;
    private static final int DIFFICULTY_TOKENS = 12;
    private static final int LOOKUP_TOKENS = 120;
        private static final long API_KEY_CACHE_MS = 60_000L;
        private static final String DEFAULT_FALLBACK_MODEL = "deepseek-chat";

    private static final int EXERCISE_TOKENS = 880;
    private static final int EVAL_TOKENS = 130;
    private static final int ARTICLE_CHAT_TOKENS = 700;


    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.fast-model:deepseek-chat}")
    private String fastModel;

    @Value("${deepseek.max-tokens}")
    private Integer maxTokens;

    private final SystemConfigService systemConfigService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String runtimeModel;
    private volatile String cachedApiKey;
    private volatile long apiKeyCacheAt;

    public AIServiceImpl(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
        this.restTemplate = buildRestTemplate();
    }

    @SuppressWarnings("null")
    private static RestTemplate buildRestTemplate() {
        System.setProperty("java.net.useSystemProxies", "true");
        SystemDefaultRoutePlanner routePlanner =
                new SystemDefaultRoutePlanner(java.net.ProxySelector.getDefault());
        CloseableHttpClient httpClient = HttpClients.custom()
                .setRoutePlanner(routePlanner)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    private String chat(String systemPrompt, String userContent, Integer requestMaxTokens, Double temperature) {
        return chatInternal(systemPrompt, userContent, requestMaxTokens, temperature, false);
    }

    private String chatFast(String systemPrompt, String userContent, Integer requestMaxTokens, Double temperature) {
        return chatInternal(systemPrompt, userContent, requestMaxTokens, temperature, true);
    }

    private String chatInternal(String systemPrompt,
                                String userContent,
                                Integer requestMaxTokens,
                                Double temperature,
                                boolean preferFastModel) {
        String resolvedApiKey = getCachedDeepseekApiKey();
        if (!StringUtils.hasText(resolvedApiKey) || resolvedApiKey.contains("your_deepseek_api_key")) {
            return "";
        }
        String modelToUse = resolveModel(preferFastModel);
        return chatWithModel(systemPrompt, userContent, requestMaxTokens, temperature, modelToUse, resolvedApiKey, true);
    }

    @SuppressWarnings("null")
    private String chatWithModel(String systemPrompt,
                                 String userContent,
                                 Integer requestMaxTokens,
                                 Double temperature,
                                 String modelName,
                                 String resolvedApiKey,
                                 boolean allowFallback) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resolvedApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
                body.put("max_tokens", Math.max(64, requestMaxTokens == null ? maxTokens : requestMaxTokens));
                body.put("temperature", temperature == null ? 0.2 : temperature);
                body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            runtimeModel = modelName;
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            if (body != null
                    && body.contains("Model Not Exist")
                    && allowFallback) {
                String fallbackModel = resolveFallbackModel();
                if (!fallbackModel.equalsIgnoreCase(modelName)) {
                    runtimeModel = fallbackModel;
                    log.warn("Configured model {} not found, fallback to {}", modelName, fallbackModel);
                    return chatWithModel(systemPrompt, userContent, requestMaxTokens, temperature, fallbackModel, resolvedApiKey, false);
                }
            }
            log.error("DeepSeek API call failed. status={}, body={}", e.getStatusCode().value(), body);
            return "";
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            return "";
        }
    }

    @Override
    public String generateSummary(String content) {
        String result = chat(
                "You are an English learning assistant. Return a concise 2-3 sentence summary.",
                content.length() > 1500 ? content.substring(0, 1500) : content,
                SUMMARY_TOKENS,
                0.25
        );
        return result.isBlank() ? content.substring(0, Math.min(200, content.length())) : result;
    }

    @Override
    public String extractKeywords(String content) {
        String result = chat(
                "Extract 5-8 key vocabulary words from this article. Return only the words separated by commas, no explanation.",
                content.length() > 1200 ? content.substring(0, 1200) : content,
                KEYWORD_TOKENS,
                0.1
        );
        return result.isBlank() ? "" : result;
    }

    @Override
    public String assessDifficulty(String content) {
        String result = chat(
                "Assess the English difficulty level of this article. Reply with only one word: EASY, MEDIUM, or HARD.",
            content.length() > 900 ? content.substring(0, 900) : content,
            DIFFICULTY_TOKENS,
            0.0
        );
        if (result.toUpperCase().contains("EASY")) return "EASY";
        if (result.toUpperCase().contains("HARD")) return "HARD";
        return "MEDIUM";
    }

    @Override
    public Map<String, String> lookupWord(String word, String context) {
        return lookupText(word, context, "word");
    }

    @Override
    public Map<String, String> lookupText(String text, String context, String type) {
        String shortenedContext = context == null ? "" : context;
        if (shortenedContext.length() > 240) {
            shortenedContext = shortenedContext.substring(0, 240);
        }
        String prompt = String.format(
                "Text: \"%s\"\nContext: \"%s\"\nType: %s\n" +
                "Return compact JSON with keys definition, chinese, example. " +
                "If type is sentence, definition should explain the sentence meaning in English and example can be empty.",
                text, shortenedContext, type == null ? "word" : type);
        String result = chatFast("You are an English learning assistant.", prompt, LOOKUP_TOKENS, 0.1);
        try {
            // clean markdown code blocks if present
            result = result.replaceAll("```json|```", "").trim();
            JsonNode node = objectMapper.readTree(result);
            Map<String, String> map = new HashMap<>();
            map.put("definition", node.path("definition").asText());
            map.put("chinese", node.path("chinese").asText());
            map.put("example", node.path("example").asText());
            return map;
        } catch (Exception e) {
            return Map.of("definition", result, "chinese", "", "example", "");
        }
    }

    private String getCachedDeepseekApiKey() {
        long now = System.currentTimeMillis();
        if (cachedApiKey != null && (now - apiKeyCacheAt) < API_KEY_CACHE_MS) {
            return cachedApiKey;
        }
        String fresh = systemConfigService.getDeepseekApiKey();
        cachedApiKey = fresh;
        apiKeyCacheAt = now;
        return fresh;
    }

    private String resolveModel(boolean preferFastModel) {
        if (preferFastModel && StringUtils.hasText(fastModel)) {
            return fastModel.trim();
        }
        if (StringUtils.hasText(runtimeModel)) {
            return runtimeModel;
        }
        return model;
    }

    private String resolveFallbackModel() {
        if (StringUtils.hasText(fastModel)) {
            return fastModel.trim();
        }
        return DEFAULT_FALLBACK_MODEL;
    }

    @Override
    public List<Map<String, Object>> generateExercises(String content, String level, Integer count) {
        int exerciseCount = count == null ? 5 : Math.max(3, Math.min(8, count));
        String shortenedContent = content.length() > 1200 ? content.substring(0, 1200) : content;
        String prompt = String.format(
                "Article:\n%s\n\n" +
            "Generate %d English learning multiple-choice exercises for a %s student. " +
            "Must include vocabulary, comprehension and grammar types. " +
            "Each question has exactly 4 options (A-D), short explanation <= 28 words. " +
            "Return only JSON array with items: " +
            "{\"type\":\"COMPREHENSION\",\"question\":\"...\",\"options\":[\"A. ...\",\"B. ...\",\"C. ...\",\"D. ...\"],\"correctAnswer\":\"A\",\"explanation\":\"...\"}",
            shortenedContent, exerciseCount, level);

        String result = chat("You are an English teacher creating quiz questions.", prompt, EXERCISE_TOKENS, 0.35);
        try {
            result = result.replaceAll("```json|```", "").trim();
            JsonNode array = objectMapper.readTree(result);
            List<Map<String, Object>> exercises = new ArrayList<>();
            for (JsonNode node : array) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("type", node.path("type").asText("COMPREHENSION"));
                ex.put("question", node.path("question").asText());
                ex.put("options", node.path("options").toString());
                ex.put("correctAnswer", node.path("correctAnswer").asText());
                ex.put("explanation", node.path("explanation").asText());
                exercises.add(ex);
            }
            return exercises.isEmpty() ? fallbackExercises(shortenedContent, exerciseCount) : exercises;
        } catch (Exception e) {
            log.error("Failed to parse exercise JSON", e);
            return fallbackExercises(shortenedContent, exerciseCount);
        }
    }

    @Override
    public String evaluateAnswer(String question, String userAnswer, String correctAnswer, String context) {
        String prompt = String.format(
                "Question: %s\nUser's answer: %s\nCorrect answer: %s\nContext: %s\n" +
                "Give a concise explanation in <=2 English sentences and one short Chinese hint. Keep it under 80 words.",
                question, userAnswer, correctAnswer, context);
            return chat("You are a fast and clear English teacher.", prompt, EVAL_TOKENS, 0.1);
    }

    @Override
    public String chatAboutArticle(String articleContent, String question, List<Map<String, String>> history) {
        String snippet = articleContent == null ? "" : articleContent.substring(0, Math.min(3500, articleContent.length()));
        StringBuilder historyText = new StringBuilder();
        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.getOrDefault("role", "user");
                String content = h.getOrDefault("content", "");
                if (StringUtils.hasText(content)) {
                    historyText.append(role).append(": ").append(content).append("\n");
                }
            }
        }
        String prompt = "Article content:\n" + snippet + "\n\nConversation history:\n" + historyText +
                "\nUser question:\n" + question;
        String answer = chat(
                "You are an article reading copilot. Answer only based on the article. If unclear, say what is missing.",
                prompt,
                ARTICLE_CHAT_TOKENS,
                0.2
        );
        if (!StringUtils.hasText(answer)) {
            return "当前 AI 服务不可用，请稍后重试。";
        }
        return answer;
    }

    private List<Map<String, Object>> fallbackExercises(String content, int count) {
        String[] sentences = content == null ? new String[0] : content.split("\\n");
        List<String> usable = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s == null ? "" : s.trim();
            if (trimmed.length() >= 35) usable.add(trimmed);
            if (usable.size() >= 3) break;
        }

        List<Map<String, Object>> list = new ArrayList<>();
        int total = Math.max(3, count);
        for (int i = 0; i < total; i++) {
            String base = usable.isEmpty() ? "English reading improves vocabulary and comprehension over time." : usable.get(i % usable.size());
            String stem = base.length() > 90 ? base.substring(0, 90) + "..." : base;

            Map<String, Object> ex = new HashMap<>();
            ex.put("type", i % 3 == 0 ? "VOCABULARY" : (i % 3 == 1 ? "COMPREHENSION" : "GRAMMAR"));
            ex.put("question", "What is the best interpretation of this sentence? " + stem);
            ex.put("options", "[\"A. It explains a key point from the article\",\"B. It introduces unrelated data\",\"C. It is an advertisement statement\",\"D. It denies the article topic\"]");
            ex.put("correctAnswer", "A");
            ex.put("explanation", "The sentence summarizes or supports the article's main idea.");
            list.add(ex);
        }
        return list;
    }
}
