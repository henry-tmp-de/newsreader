package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newsreader.dto.NewsFetchResultDTO;
import com.newsreader.entity.Article;
import com.newsreader.mapper.ArticleMapper;
import com.newsreader.service.ArticleEnrichmentService;
import com.newsreader.service.ArticleService;
import com.newsreader.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ArticleServiceImpl implements ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleServiceImpl.class);

    private final ArticleMapper articleMapper;
    private final SystemConfigService systemConfigService;
    private final ArticleEnrichmentService articleEnrichmentService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String NEWS_API_BASE_URL = "https://newsapi.org/v2";
    private static final int MIN_ARTICLE_CONTENT_LENGTH = 120;

    public ArticleServiceImpl(ArticleMapper articleMapper,
                              SystemConfigService systemConfigService,
                              ArticleEnrichmentService articleEnrichmentService,
                              ObjectMapper objectMapper) {
        this.articleMapper = articleMapper;
        this.systemConfigService = systemConfigService;
        this.articleEnrichmentService = articleEnrichmentService;
        this.restTemplate = buildRestTemplate();
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("null")
    private static RestTemplate buildRestTemplate() {
        // 让 Java 读取 Windows 系统代理设置（如 Clash/V2Ray）
        System.setProperty("java.net.useSystemProxies", "true");
        SystemDefaultRoutePlanner routePlanner =
            new SystemDefaultRoutePlanner(java.net.ProxySelector.getDefault());
        CloseableHttpClient httpClient = HttpClients.custom()
            .setRoutePlanner(routePlanner)
            .build();
        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
        }

    @Override
    public Page<Article> getArticles(Integer pageNum, Integer pageSize,
                                      String category, String difficulty, String keyword) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(Article::getCategory, category);
        }
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq(Article::getDifficulty, difficulty);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Article::getTitle, keyword)
                   .or()
                   .like(Article::getSummary, keyword);
        }
        wrapper.orderByDesc(Article::getPublishedAt);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Article getById(Long id) {
        return articleMapper.selectById(id);
    }

    @Override
    @Scheduled(fixedDelay = 3600000) // 每小时拉取一次
    @SuppressWarnings("null")
    public NewsFetchResultDTO fetchAndSaveFromNewsAPI() {
        return fetchAndSaveFromNewsAPI(null, 10);
    }

    @Override
    @SuppressWarnings("null")
    public NewsFetchResultDTO fetchAndSaveFromNewsAPI(List<String> categories, Integer pageSize) {
        String newsApiKey = systemConfigService.getNewsApiKey();
        if (!StringUtils.hasText(newsApiKey) || newsApiKey.contains("your_newsapi_key")) {
            throw new RuntimeException("请先在系统配置中填写 NewsAPI Key");
        }

        NewsFetchResultDTO result = new NewsFetchResultDTO();
        List<String> selectedCategories = normalizeCategories(categories);
        int safePageSize = normalizePageSize(pageSize);

        for (String category : selectedCategories) {
            try {
                String url = String.format("%s/top-headlines?category=%s&language=en&pageSize=%d&apiKey=%s",
                    NEWS_API_BASE_URL, category, safePageSize, newsApiKey);
                var responseEntity = restTemplate.getForEntity(java.net.URI.create(url), String.class);
                String response = responseEntity.getBody();
                if (!StringUtils.hasText(response)) {
                    result.setFailed(result.getFailed() + 1);
                    continue;
                }
                JsonNode root = objectMapper.readTree(response);

                if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
                    result.setFailed(result.getFailed() + 1);
                    log.warn("NewsAPI error: status={}, code={}, message={}",
                            root.path("status").asText(),
                            root.path("code").asText(),
                            root.path("message").asText());
                    continue;
                }

                JsonNode articles = root.get("articles");
                if (articles != null && articles.isArray()) {
                    for (JsonNode node : articles) {
                        result.setFetched(result.getFetched() + 1);
                        SaveOutcome outcome = saveArticleFromNode(node, category);
                        if (outcome == SaveOutcome.INSERTED) {
                            result.setInserted(result.getInserted() + 1);
                        } else if (outcome == SaveOutcome.DUPLICATED) {
                            result.setDuplicated(result.getDuplicated() + 1);
                        } else if (outcome == SaveOutcome.SKIPPED_NO_CONTENT) {
                            result.setSkippedNoContent(result.getSkippedNoContent() + 1);
                        } else {
                            result.setFailed(result.getFailed() + 1);
                        }
                    }
                }
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                log.error("Failed to fetch news for category: {}", category, e);
            }
        }

        result.setEnqueuedForEnrichment(result.getInserted());
        articleEnrichmentService.enrichPendingArticlesAsync();
        return result;
    }

    private List<String> normalizeCategories(List<String> categories) {
        List<String> all = List.of("technology", "science", "health", "business", "sports");
        if (categories == null || categories.isEmpty()) {
            return all;
        }
        List<String> normalized = categories.stream()
                .map(v -> v == null ? "" : v.trim().toLowerCase())
                .filter(all::contains)
                .distinct()
                .toList();
        return normalized.isEmpty() ? all : normalized;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) return 10;
        return Math.max(1, Math.min(50, pageSize));
    }

    @Override
    public int deleteArticleById(Long id) {
        if (id == null) {
            return 0;
        }
        return articleMapper.deleteById(id);
    }

    @Override
    public int deleteArticlesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Long> safeIds = ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (safeIds.isEmpty()) {
            return 0;
        }
        return articleMapper.deleteBatchIds(safeIds);
    }

    @Override
    public int clearAllArticles() {
        int totalDeleted = 0;
        while (true) {
            List<Long> ids = articleMapper.selectList(
                            new LambdaQueryWrapper<Article>()
                                    .select(Article::getId)
                                    .orderByAsc(Article::getId)
                                    .last("limit 500"))
                    .stream()
                    .map(Article::getId)
                    .filter(id -> id != null && id > 0)
                    .toList();

            if (ids.isEmpty()) {
                break;
            }

            int deleted = articleMapper.deleteBatchIds(ids);
            totalDeleted += deleted;
            if (deleted <= 0) {
                break;
            }
        }
        return totalDeleted;
    }

    private SaveOutcome saveArticleFromNode(JsonNode node, String category) {
        try {
            String url = node.path("url").asText();
            if (!StringUtils.hasText(url)) {
                return SaveOutcome.FAILED;
            }
            // 检查是否已存在
            Article existed = articleMapper.selectOne(
                    new LambdaQueryWrapper<Article>().eq(Article::getUrl, url).last("limit 1"));
            if (existed != null) {
                backfillExistingArticleIfNeeded(existed, node, url);
                return SaveOutcome.DUPLICATED;
            }

            String content = resolveArticleContent(node, url);
            if (!StringUtils.hasText(content) || content.length() < 30) {
                return SaveOutcome.SKIPPED_NO_CONTENT;
            }

            Article article = new Article();
            article.setTitle(node.path("title").asText());
            article.setContent(content);
            article.setUrl(url);
            article.setSource(node.path("source").path("name").asText());
            article.setAuthor(node.path("author").asText());
            article.setCategory(category);

            String publishedAt = node.path("publishedAt").asText();
            if (StringUtils.hasText(publishedAt)) {
                article.setPublishedAt(parsePublishedAt(publishedAt));
            }

            // 方案B：先入库原始新闻，再异步增强AI字段
            article.setSummary(null);
            article.setKeywords(null);
            article.setDifficulty("MEDIUM");

            articleMapper.insert(article);
            return SaveOutcome.INSERTED;
        } catch (Exception e) {
            log.error("Failed to save article", e);
            return SaveOutcome.FAILED;
        }
    }

    private String resolveArticleContent(JsonNode node, String url) {
        String newsApiContent = node.path("content").asText();
        String description = node.path("description").asText();

        boolean shouldFetchOriginal = !StringUtils.hasText(newsApiContent)
                || newsApiContent.length() < MIN_ARTICLE_CONTENT_LENGTH
                || isLikelyTruncatedByNewsApi(newsApiContent);

        if (shouldFetchOriginal && StringUtils.hasText(url)) {
            String fullContent = fetchFullContentFromUrl(url);
            if (StringUtils.hasText(fullContent) && fullContent.length() >= MIN_ARTICLE_CONTENT_LENGTH) {
                return fullContent;
            }
        }

        if (StringUtils.hasText(newsApiContent) && newsApiContent.length() >= 80) {
            return newsApiContent;
        }
        return description;
    }

    private void backfillExistingArticleIfNeeded(Article existed, JsonNode node, String url) {
        if (existed == null) {
            return;
        }
        String current = existed.getContent();
        if (!isContentIncomplete(current)) {
            return;
        }
        String resolved = resolveArticleContent(node, url);
        if (!StringUtils.hasText(resolved) || resolved.length() <= currentLength(current)) {
            return;
        }
        existed.setContent(resolved);
        articleMapper.updateById(existed);
    }

    private boolean isContentIncomplete(String content) {
        return !StringUtils.hasText(content)
                || content.length() < MIN_ARTICLE_CONTENT_LENGTH
                || isLikelyTruncatedByNewsApi(content);
    }

    private int currentLength(String content) {
        return content == null ? 0 : content.length();
    }

    private boolean isLikelyTruncatedByNewsApi(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        return content.contains("[+") && content.contains("chars]");
    }

    private String fetchFullContentFromUrl(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15_000)
                    .get();

            List<String> candidates = new ArrayList<>();
            Elements articleNodes = doc.select("article, main article, [itemprop=articleBody], .article-content, .post-content, .entry-content");
            for (Element node : articleNodes) {
                String text = extractReadableText(node);
                if (StringUtils.hasText(text)) {
                    candidates.add(text);
                }
            }

            String mainText = extractReadableText(doc.body());
            if (StringUtils.hasText(mainText)) {
                candidates.add(mainText);
            }

            return candidates.stream()
                    .filter(StringUtils::hasText)
                    .max((a, b) -> Integer.compare(a.length(), b.length()))
                    .orElse("");
        } catch (Exception e) {
            log.debug("Failed to fetch full content from url: {}", url, e);
            return "";
        }
    }

    private String extractReadableText(Element root) {
        if (root == null) {
            return "";
        }
        Elements paragraphs = root.select("p");
        StringBuilder sb = new StringBuilder();
        for (Element p : paragraphs) {
            String line = p.text();
            if (StringUtils.hasText(line) && line.length() >= 40) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        return root.text();
    }

    private LocalDateTime parsePublishedAt(String publishedAt) {
        try {
            return OffsetDateTime.parse(publishedAt).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(publishedAt, DateTimeFormatter.ISO_DATE_TIME);
        }
    }

    private enum SaveOutcome {
        INSERTED,
        DUPLICATED,
        SKIPPED_NO_CONTENT,
        FAILED
    }
}
