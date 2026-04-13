package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newsreader.entity.Article;
import com.newsreader.mapper.ArticleMapper;
import com.newsreader.service.AIService;
import com.newsreader.service.ArticleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ArticleServiceImpl implements ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleServiceImpl.class);

    private final ArticleMapper articleMapper;
    private final AIService aiService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${news-api.key}")
    private String newsApiKey;

    @Value("${news-api.base-url}")
    private String newsApiBaseUrl;

    public ArticleServiceImpl(ArticleMapper articleMapper, AIService aiService, ObjectMapper objectMapper) {
        this.articleMapper = articleMapper;
        this.aiService = aiService;
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
    public void fetchAndSaveFromNewsAPI() {
        String[] categories = {"technology", "science", "health", "business", "sports"};
        for (String category : categories) {
            try {
                String url = String.format("%s/top-headlines?category=%s&language=en&pageSize=10&apiKey=%s",
                        newsApiBaseUrl, category, newsApiKey);
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                JsonNode articles = root.get("articles");
                if (articles != null && articles.isArray()) {
                    for (JsonNode node : articles) {
                        saveArticleFromNode(node, category);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch news for category: {}", category, e);
            }
        }
    }

    private void saveArticleFromNode(JsonNode node, String category) {
        try {
            String url = node.path("url").asText();
            // 检查是否已存在
            Long count = articleMapper.selectCount(
                    new LambdaQueryWrapper<Article>().eq(Article::getUrl, url));
            if (count > 0) return;

            String content = node.path("content").asText();
            if (!StringUtils.hasText(content) || content.length() < 100) return;

            Article article = new Article();
            article.setTitle(node.path("title").asText());
            article.setContent(content);
            article.setUrl(url);
            article.setSource(node.path("source").path("name").asText());
            article.setAuthor(node.path("author").asText());
            article.setCategory(category);

            String publishedAt = node.path("publishedAt").asText();
            if (StringUtils.hasText(publishedAt)) {
                article.setPublishedAt(LocalDateTime.parse(publishedAt,
                        DateTimeFormatter.ISO_DATE_TIME));
            }

            // AI处理：生成摘要、提取关键词、评估难度
            article.setSummary(aiService.generateSummary(content));
            article.setKeywords(aiService.extractKeywords(content));
            article.setDifficulty(aiService.assessDifficulty(content));

            articleMapper.insert(article);
        } catch (Exception e) {
            log.error("Failed to save article", e);
        }
    }
}
