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
import java.util.regex.Pattern;

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
    private static final int MIN_ARTICLE_WORD_COUNT = 30;
    private static final double EXTRA_FETCH_RATIO = 0.15;
    private static final int MAX_FETCH_ROUNDS_PER_CATEGORY = 8;
    private static final Pattern TRUNCATED_SUFFIX_PATTERN = Pattern.compile("\\[\\+\\d+\\s+chars]$");

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
        Page<Article> page = articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        page.setRecords(page.getRecords().stream()
            .filter(a -> a != null && isContentQualified(a.getContent()))
            .toList());
        return page;
    }

    @Override
    public Article getById(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            return null;
        }
        return isContentQualified(article.getContent()) ? article : null;
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

        int purgedInvalid = purgeInvalidArticles();
        if (purgedInvalid > 0) {
            log.info("Purged {} invalid historical articles before fetch", purgedInvalid);
        }

        NewsFetchResultDTO result = new NewsFetchResultDTO();
        List<String> selectedCategories = normalizeCategories(categories);
        int safePageSize = normalizePageSize(pageSize);

        for (String category : selectedCategories) {
            fetchCategoryUntilTarget(category, safePageSize, newsApiKey, result);
        }

        result.setEnqueuedForEnrichment(result.getInserted());
        articleEnrichmentService.enrichPendingArticlesAsync();
        return result;
    }

    @SuppressWarnings("null")
    private void fetchCategoryUntilTarget(String category, int targetInsertCount, String newsApiKey, NewsFetchResultDTO result) {
        int insertedForCategory = 0;
        int page = 1;
        int rounds = 0;

        while (insertedForCategory < targetInsertCount && rounds < MAX_FETCH_ROUNDS_PER_CATEGORY) {
            try {
                int remaining = targetInsertCount - insertedForCategory;
                int requestPageSize = (int) Math.ceil(remaining * (1 + EXTRA_FETCH_RATIO));
                requestPageSize = Math.max(1, Math.min(50, requestPageSize));

                String url = String.format(
                        "%s/top-headlines?category=%s&language=en&pageSize=%d&page=%d&apiKey=%s",
                        NEWS_API_BASE_URL, category, requestPageSize, page, newsApiKey);

                var responseEntity = restTemplate.getForEntity(url, String.class);
                String response = responseEntity.getBody();
                if (!StringUtils.hasText(response)) {
                    result.setFailed(result.getFailed() + 1);
                    break;
                }

                JsonNode root = objectMapper.readTree(response);
                if (!"ok".equalsIgnoreCase(root.path("status").asText())) {
                    result.setFailed(result.getFailed() + 1);
                    log.warn("NewsAPI error: category={}, page={}, status={}, code={}, message={}",
                            category,
                            page,
                            root.path("status").asText(),
                            root.path("code").asText(),
                            root.path("message").asText());
                    break;
                }

                JsonNode articles = root.get("articles");
                if (articles == null || !articles.isArray() || articles.isEmpty()) {
                    break;
                }

                for (JsonNode node : articles) {
                    result.setFetched(result.getFetched() + 1);
                    SaveOutcome outcome = saveArticleFromNode(node, category);
                    if (outcome == SaveOutcome.INSERTED) {
                        result.setInserted(result.getInserted() + 1);
                        insertedForCategory++;
                    } else if (outcome == SaveOutcome.DUPLICATED) {
                        result.setDuplicated(result.getDuplicated() + 1);
                    } else if (outcome == SaveOutcome.SKIPPED_NO_CONTENT) {
                        result.setSkippedNoContent(result.getSkippedNoContent() + 1);
                    } else {
                        result.setFailed(result.getFailed() + 1);
                    }

                    if (insertedForCategory >= targetInsertCount) {
                        break;
                    }
                }

                page++;
                rounds++;
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                log.error("Failed to fetch news for category: {}, page: {}", category, page, e);
                break;
            }
        }

        if (insertedForCategory < targetInsertCount) {
            log.warn("Category {} reached fetch limit before target: inserted={}, target={}",
                    category, insertedForCategory, targetInsertCount);
        }
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
            if (!isContentQualified(content)) {
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
            if (StringUtils.hasText(fullContent)
                    && fullContent.length() >= MIN_ARTICLE_CONTENT_LENGTH
                    && !isLikelyTruncatedByNewsApi(fullContent)) {
                return fullContent;
            }
        }

        if (StringUtils.hasText(newsApiContent)
                && newsApiContent.length() >= 80
                && !isLikelyTruncatedByNewsApi(newsApiContent)) {
            return newsApiContent;
        }
        if (StringUtils.hasText(description) && !isLikelyTruncatedByNewsApi(description)) {
            return description;
        }
        return "";
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
        if (!isContentQualified(resolved) || resolved.length() <= currentLength(current)) {
            return;
        }
        existed.setContent(resolved);
        articleMapper.updateById(existed);
    }

    private boolean isContentIncomplete(String content) {
        return !isContentQualified(content)
            || content.length() < MIN_ARTICLE_CONTENT_LENGTH;
        }

        private boolean isContentQualified(String content) {
        return StringUtils.hasText(content)
            && !isLikelyTruncatedByNewsApi(content)
            && getWordCount(content) >= MIN_ARTICLE_WORD_COUNT;
    }

    private int getWordCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    private int purgeInvalidArticles() {
        int deleted = 0;
        long lastId = 0L;
        while (true) {
            List<Article> batch = articleMapper.selectList(
                    new LambdaQueryWrapper<Article>()
                            .select(Article::getId, Article::getContent)
                            .gt(Article::getId, lastId)
                            .orderByAsc(Article::getId)
                            .last("limit 500"));
            if (batch == null || batch.isEmpty()) {
                break;
            }

            lastId = batch.get(batch.size() - 1).getId();

            List<Long> invalidIds = batch.stream()
                    .filter(a -> a != null && a.getId() != null)
                    .filter(a -> !isContentQualified(a.getContent()))
                    .map(Article::getId)
                    .toList();

            if (!invalidIds.isEmpty()) {
                deleted += articleMapper.deleteBatchIds(invalidIds);
            }
        }
        return deleted;
    }

    private int currentLength(String content) {
        return content == null ? 0 : content.length();
    }

    private boolean isLikelyTruncatedByNewsApi(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        return TRUNCATED_SUFFIX_PATTERN.matcher(content.trim()).find();
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
