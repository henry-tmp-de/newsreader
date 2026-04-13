package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.newsreader.dto.RecommendedArticleDTO;
import com.newsreader.dto.UserProfileDTO;
import com.newsreader.entity.Article;
import com.newsreader.entity.LearningRecord;
import com.newsreader.entity.User;
import com.newsreader.mapper.ArticleMapper;
import com.newsreader.mapper.LearningRecordMapper;
import com.newsreader.mapper.UserMapper;
import com.newsreader.mapper.UserVocabularyMapper;
import com.newsreader.service.RecommendationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final UserVocabularyMapper userVocabularyMapper;

    public RecommendationServiceImpl(UserMapper userMapper,
                                     ArticleMapper articleMapper,
                                     LearningRecordMapper learningRecordMapper,
                                     UserVocabularyMapper userVocabularyMapper) {
        this.userMapper = userMapper;
        this.articleMapper = articleMapper;
        this.learningRecordMapper = learningRecordMapper;
        this.userVocabularyMapper = userVocabularyMapper;
    }

    @Override
    public UserProfileDTO buildUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserProfileDTO dto = new UserProfileDTO();
        dto.setUserId(userId);
        dto.setLevel(normalizeLevel(user.getLevel()));
        dto.setInterests(parseInterests(user.getInterests()));

        Integer readArticles = defaultZero(learningRecordMapper.getReadArticleCount(userId));
        Integer completedArticles = defaultZero(learningRecordMapper.getCompletedArticleCount(userId));
        Integer avgReadDurationSec = defaultZero(learningRecordMapper.getAvgReadDuration(userId));
        Integer lookupCount = defaultZero(learningRecordMapper.getLookupCount(userId));
        Integer activeDays14 = defaultZero(learningRecordMapper.getActiveDays14(userId));

        Map<String, Object> exerciseStats = learningRecordMapper.getExerciseStats(userId);
        int totalExercises = toInt(exerciseStats == null ? null : exerciseStats.get("total"));
        int correctExercises = toInt(exerciseStats == null ? null : exerciseStats.get("correct_count"));
        int accuracy = totalExercises > 0 ? (int) Math.round(correctExercises * 100.0 / totalExercises) : 0;

        long vocabSize = userVocabularyMapper.selectCount(
                new LambdaQueryWrapper<com.newsreader.entity.UserVocabulary>()
                        .eq(com.newsreader.entity.UserVocabulary::getUserId, userId));

        int abilityScore = estimateAbilityScore(dto.getLevel(), accuracy, avgReadDurationSec, (int) vocabSize, activeDays14);
        String targetDifficulty = estimateTargetDifficulty(dto.getLevel(), abilityScore);

        dto.setReadArticles(readArticles);
        dto.setCompletedArticles(completedArticles);
        dto.setAvgReadDurationSec(avgReadDurationSec);
        dto.setExerciseAccuracy(accuracy);
        dto.setLookupCount(lookupCount);
        dto.setActiveDays14(activeDays14);
        dto.setAbilityScore(abilityScore);
        dto.setTargetDifficulty(targetDifficulty);
        return dto;
    }

    @Override
    public List<RecommendedArticleDTO> recommendArticles(Long userId, Integer size) {
        int safeSize = Math.max(3, Math.min(size == null ? 12 : size, 30));
        UserProfileDTO profile = buildUserProfile(userId);

        List<Article> candidates = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .orderByDesc(Article::getPublishedAt)
                        .orderByDesc(Article::getCreatedAt)
                        .last("limit 200"));

        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<Long> seen = new LinkedHashSet<>(learningRecordMapper.getRecentInteractedArticleIds(userId, 200));
        Set<Long> disliked = new LinkedHashSet<>(learningRecordMapper.getDislikedArticleIds(userId));
        Map<String, Integer> categoryPreference = toCategoryPreferenceMap(
                learningRecordMapper.getCategoryPreferenceScores(userId));

        List<RecommendedArticleDTO> ranked = new ArrayList<>();
        for (Article article : candidates) {
            if (article.getId() == null || seen.contains(article.getId()) || disliked.contains(article.getId())) {
                continue;
            }
            ScoredReason scored = scoreArticle(profile, article, categoryPreference);
            RecommendedArticleDTO dto = new RecommendedArticleDTO();
            dto.setId(article.getId());
            dto.setTitle(article.getTitle());
            dto.setSummary(article.getSummary());
            dto.setSource(article.getSource());
            dto.setCategory(article.getCategory());
            dto.setDifficulty(resolveDifficulty(article));
            dto.setKeywords(article.getKeywords());
            dto.setPublishedAt(article.getPublishedAt());
            dto.setScore(scored.score());
            dto.setReasonTags(scored.reasons());
            ranked.add(dto);
        }

        return ranked.stream()
                .sorted(Comparator.comparing(RecommendedArticleDTO::getScore).reversed()
                        .thenComparing(RecommendedArticleDTO::getPublishedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeSize)
                .collect(Collectors.toList());
    }

    @Override
    public void updateInterests(Long userId, List<String> interests) {
        List<String> normalized = (interests == null ? List.<String>of() : interests).stream()
                .map(v -> v == null ? "" : v.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .collect(Collectors.toList());

        User patch = new User();
        patch.setId(userId);
        patch.setInterests(String.join(",", normalized));
        userMapper.updateById(patch);
    }

    @Override
    public void recordArticleFeedback(Long userId, Long articleId, String feedbackType) {
        if (userId == null || articleId == null) {
            throw new RuntimeException("参数不完整");
        }
        if (articleMapper.selectById(articleId) == null) {
            throw new RuntimeException("文章不存在");
        }

        String normalized = normalizeFeedbackType(feedbackType);
        if (normalized == null) {
            throw new RuntimeException("反馈类型仅支持 LIKE 或 DISLIKE");
        }

        learningRecordMapper.delete(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getArticleId, articleId)
                .in(LearningRecord::getActionType, List.of("ARTICLE_LIKE", "ARTICLE_DISLIKE")));

        LearningRecord record = new LearningRecord();
        record.setUserId(userId);
        record.setArticleId(articleId);
        record.setActionType(normalized);
        record.setNote("USER_FEEDBACK");
        learningRecordMapper.insert(record);
    }

    private ScoredReason scoreArticle(UserProfileDTO profile, Article article, Map<String, Integer> categoryPreference) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        String targetDifficulty = profile.getTargetDifficulty();
        String articleDifficulty = resolveDifficulty(article);

        int diffScore = difficultyMatchScore(targetDifficulty, articleDifficulty);
        score += diffScore;
        if (diffScore >= 22) {
            reasons.add("难度匹配 i+1");
        } else if (diffScore >= 10) {
            reasons.add("难度接近");
        }

        int interestScore = interestMatchScore(profile.getInterests(), article);
        score += interestScore;
        if (interestScore >= 20) {
            reasons.add("兴趣高度相关");
        } else if (interestScore > 0) {
            reasons.add("兴趣部分相关");
        }

        int feedbackScore = feedbackPreferenceScore(categoryPreference, article);
        score += feedbackScore;
        if (feedbackScore >= 10) {
            reasons.add("历史偏好强化");
        } else if (feedbackScore < 0) {
            reasons.add("已降权推荐");
        }

        int freshnessScore = freshnessScore(article.getPublishedAt(), article.getCreatedAt());
        score += freshnessScore;
        if (freshnessScore >= 14) {
            reasons.add("新闻时效性高");
        }

        int densityScore = infoDensityBonus(article);
        score += densityScore;
        if (densityScore >= 10) {
            reasons.add("信息密度适中");
        }

        if (reasons.isEmpty()) {
            reasons.add("综合推荐");
        }

        return new ScoredReason(Math.max(0, Math.min(100, score)), reasons);
    }

    private int interestMatchScore(List<String> interests, Article article) {
        if (interests == null || interests.isEmpty()) {
            return 8;
        }

        Set<String> interestSet = interests.stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        int score = 0;
        String category = safeLower(article.getCategory());
        if (interestSet.contains(category)) {
            score += 20;
        }

        String title = safeLower(article.getTitle());
        String keywords = safeLower(article.getKeywords());
        for (String interest : interestSet) {
            if (interest.length() < 2) {
                continue;
            }
            if (title.contains(interest) || keywords.contains(interest)) {
                score += 8;
            }
        }
        return Math.min(score, 35);
    }

    private int difficultyMatchScore(String target, String actual) {
        int t = diffRank(target);
        int a = diffRank(actual);
        int gap = Math.abs(t - a);
        if (gap == 0) return 28;
        if (gap == 1) return 16;
        return 4;
    }

    private int feedbackPreferenceScore(Map<String, Integer> categoryPreference, Article article) {
        if (categoryPreference == null || categoryPreference.isEmpty()) {
            return 0;
        }
        String category = safeLower(article.getCategory());
        int raw = categoryPreference.getOrDefault(category, 0);
        if (raw > 0) {
            return Math.min(raw * 4, 16);
        }
        if (raw < 0) {
            return Math.max(raw * 4, -16);
        }
        return 0;
    }

    private Map<String, Integer> toCategoryPreferenceMap(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object categoryObj = row == null ? null : row.get("category");
            String category = safeLower(categoryObj == null ? null : categoryObj.toString());
            int score = toInt(row == null ? null : row.get("score"));
            if (StringUtils.hasText(category)) {
                result.put(category, score);
            }
        }
        return result;
    }

    private String normalizeFeedbackType(String feedbackType) {
        if (!StringUtils.hasText(feedbackType)) {
            return null;
        }
        String normalized = feedbackType.trim().toUpperCase(Locale.ROOT);
        if ("LIKE".equals(normalized)) {
            return "ARTICLE_LIKE";
        }
        if ("DISLIKE".equals(normalized)) {
            return "ARTICLE_DISLIKE";
        }
        return null;
    }

    private int freshnessScore(LocalDateTime publishedAt, LocalDateTime createdAt) {
        LocalDateTime base = publishedAt != null ? publishedAt : createdAt;
        if (base == null) {
            return 8;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(base, LocalDateTime.now()));
        return (int) Math.max(0, 22 - days * 2);
    }

    private int infoDensityBonus(Article article) {
        String content = article.getContent();
        if (!StringUtils.hasText(content)) {
            return 4;
        }

        String[] words = content.split("\\s+");
        int totalWords = words.length;
        if (totalWords == 0) {
            return 4;
        }

        int rareWords = 0;
        for (String w : words) {
            String normalized = w.replaceAll("[^A-Za-z]", "");
            if (normalized.length() >= 9) {
                rareWords++;
            }
        }

        double rareRatio = rareWords * 1.0 / totalWords;
        int sentenceCount = Math.max(1, content.split("[.!?]+").length);
        double sentenceLen = totalWords * 1.0 / sentenceCount;

        int score = 6;
        if (rareRatio >= 0.08 && rareRatio <= 0.18) {
            score += 5;
        }
        if (sentenceLen >= 14 && sentenceLen <= 24) {
            score += 5;
        }
        return Math.min(score, 16);
    }

    private String resolveDifficulty(Article article) {
        String difficulty = article.getDifficulty();
        if (StringUtils.hasText(difficulty)) {
            return normalizeDifficulty(difficulty);
        }
        String content = article.getContent();
        if (!StringUtils.hasText(content)) {
            return "MEDIUM";
        }

        String[] words = content.split("\\s+");
        int totalWords = words.length;
        int sentenceCount = Math.max(1, content.split("[.!?]+").length);
        double sentenceLen = totalWords * 1.0 / sentenceCount;

        int rareWords = 0;
        for (String w : words) {
            String normalized = w.replaceAll("[^A-Za-z]", "");
            if (normalized.length() >= 9) {
                rareWords++;
            }
        }
        double rareRatio = totalWords == 0 ? 0 : rareWords * 1.0 / totalWords;

        if (sentenceLen > 24 || rareRatio > 0.18) {
            return "HARD";
        }
        if (sentenceLen < 12 && rareRatio < 0.07) {
            return "EASY";
        }
        return "MEDIUM";
    }

    private String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "BEGINNER";
        }
        String upper = level.toUpperCase(Locale.ROOT);
        if ("ADVANCED".equals(upper)) return "ADVANCED";
        if ("INTERMEDIATE".equals(upper)) return "INTERMEDIATE";
        return "BEGINNER";
    }

    private String normalizeDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return "MEDIUM";
        }
        String upper = difficulty.toUpperCase(Locale.ROOT);
        if ("EASY".equals(upper) || "HARD".equals(upper) || "MEDIUM".equals(upper)) {
            return upper;
        }
        return "MEDIUM";
    }

    private List<String> parseInterests(String interests) {
        if (!StringUtils.hasText(interests)) {
            return Collections.emptyList();
        }
        return Arrays.stream(interests.split(","))
                .map(v -> v == null ? "" : v.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private int estimateAbilityScore(String level, int accuracy, int avgReadDuration, int vocabSize, int activeDays14) {
        int base;
        switch (level) {
            case "ADVANCED":
                base = 75;
                break;
            case "INTERMEDIATE":
                base = 60;
                break;
            default:
                base = 45;
        }

        int score = base;
        score += Math.min(accuracy / 5, 15);
        score += Math.min(vocabSize / 40, 10);
        if (avgReadDuration >= 150) {
            score += 6;
        } else if (avgReadDuration >= 90) {
            score += 3;
        }
        score += Math.min(activeDays14, 10);
        return Math.max(20, Math.min(95, score));
    }

    private String estimateTargetDifficulty(String level, int abilityScore) {
        if ("ADVANCED".equals(level)) {
            return abilityScore >= 82 ? "HARD" : "MEDIUM";
        }
        if ("INTERMEDIATE".equals(level)) {
            return abilityScore >= 78 ? "HARD" : "MEDIUM";
        }
        return abilityScore >= 58 ? "MEDIUM" : "EASY";
    }

    private int diffRank(String diff) {
        switch (normalizeDifficulty(diff)) {
            case "EASY":
                return 1;
            case "HARD":
                return 3;
            default:
                return 2;
        }
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private record ScoredReason(int score, List<String> reasons) {}
}
