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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class ArticleServiceImpl implements ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleServiceImpl.class);

    private final ArticleMapper articleMapper;
    private final SystemConfigService systemConfigService;
    private final ArticleEnrichmentService articleEnrichmentService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String NEWS_API_BASE_URL = "https://newsapi.org/v2";

    public ArticleServiceImpl(ArticleMapper articleMapper,
                              SystemConfigService systemConfigService,
                              ArticleEnrichmentService articleEnrichmentService,
                              ObjectMapper objectMapper) {
        this.articleMapper = articleMapper;
        this.systemConfigService = systemConfigService;
        this.articleEnrichmentService = articleEnrichmentService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
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
        String newsApiKey = systemConfigService.getNewsApiKey();
        if (!StringUtils.hasText(newsApiKey) || newsApiKey.contains("your_newsapi_key")) {
            throw new RuntimeException("请先在系统配置中填写 NewsAPI Key");
        }

        NewsFetchResultDTO result = new NewsFetchResultDTO();
        String[] categories = {"technology", "science", "health", "business", "sports"};
        for (String category : categories) {
            try {
                String url = String.format("%s/top-headlines?category=%s&language=en&pageSize=10&apiKey=%s",
                        NEWS_API_BASE_URL, category, newsApiKey);
                var responseEntity = restTemplate.getForEntity(java.net.URI.create(url), Object.class);
                Object bodyObj = responseEntity.getBody();
                String response = bodyObj == null ? "" : bodyObj.toString();
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

    private SaveOutcome saveArticleFromNode(JsonNode node, String category) {
        try {
            String url = node.path("url").asText();
            if (!StringUtils.hasText(url)) {
                return SaveOutcome.FAILED;
            }
            // 检查是否已存在
            Long count = articleMapper.selectCount(
                    new LambdaQueryWrapper<Article>().eq(Article::getUrl, url));
            if (count > 0) {
                return SaveOutcome.DUPLICATED;
            }

            String content = node.path("content").asText();
            if (!StringUtils.hasText(content) || content.length() < 80) {
                content = node.path("description").asText();
            }
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
