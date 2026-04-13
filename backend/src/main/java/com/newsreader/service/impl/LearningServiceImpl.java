package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.newsreader.entity.LearningRecord;
import com.newsreader.entity.UserVocabulary;
import com.newsreader.mapper.LearningRecordMapper;
import com.newsreader.mapper.UserVocabularyMapper;
import com.newsreader.service.AIService;
import com.newsreader.service.LearningService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningServiceImpl implements LearningService {

    private final LearningRecordMapper recordMapper;
    private final UserVocabularyMapper vocabularyMapper;
    private final AIService aiService;

    public LearningServiceImpl(LearningRecordMapper recordMapper,
                               UserVocabularyMapper vocabularyMapper,
                               AIService aiService) {
        this.recordMapper = recordMapper;
        this.vocabularyMapper = vocabularyMapper;
        this.aiService = aiService;
    }

    @Override
    public void recordAction(Long userId, Long articleId, String actionType, Integer duration) {
        LearningRecord record = new LearningRecord();
        record.setUserId(userId);
        record.setArticleId(articleId);
        record.setActionType(actionType);
        record.setDuration(duration);
        recordMapper.insert(record);
    }

    @Override
    public Map<String, Object> getStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 已读文章数
        Integer readCount = recordMapper.getReadArticleCount(userId);
        stats.put("readArticles", readCount != null ? readCount : 0);

        // 练习统计
        Map<String, Object> exerciseStats = recordMapper.getExerciseStats(userId);
        if (exerciseStats != null) {
            Long total = exerciseStats.get("total") != null ?
                    ((Number) exerciseStats.get("total")).longValue() : 0L;
            Long correctCount = exerciseStats.get("correct_count") != null ?
                    ((Number) exerciseStats.get("correct_count")).longValue() : 0L;
            stats.put("totalExercises", total);
            stats.put("correctExercises", correctCount);
            stats.put("accuracy", total > 0 ? Math.round(correctCount * 100.0 / total) : 0);
        }

        // 词汇量
        Long vocabCount = vocabularyMapper.selectCount(
                new LambdaQueryWrapper<UserVocabulary>().eq(UserVocabulary::getUserId, userId));
        stats.put("vocabularySize", vocabCount);

        // 待复习单词
        List<UserVocabulary> reviewWords = vocabularyMapper.findReviewWords(userId, 10);
        stats.put("reviewWords", reviewWords);

        return stats;
    }

    @Override
    public Map<String, String> lookupWord(String word, String context, Long userId) {
        Map<String, String> result = aiService.lookupWord(word, context);

        // 保存到用户词汇表
        Long exists = vocabularyMapper.selectCount(
                new LambdaQueryWrapper<UserVocabulary>()
                        .eq(UserVocabulary::getUserId, userId)
                        .eq(UserVocabulary::getWord, word.toLowerCase()));

        if (exists == 0) {
            UserVocabulary vocab = new UserVocabulary();
            vocab.setUserId(userId);
            vocab.setWord(word.toLowerCase());
            vocab.setDefinition(result.get("definition"));
            vocab.setExample(result.get("example"));
            vocab.setMasteryLevel(0);
            vocab.setReviewCount(0);
            vocab.setNextReviewAt(LocalDateTime.now().plusDays(1));
            vocabularyMapper.insert(vocab);
        } else {
            // 记录查询行为
            LearningRecord record = new LearningRecord();
            record.setUserId(userId);
            record.setActionType("WORD_LOOKUP");
            record.setNote(word);
            recordMapper.insert(record);
        }

        return result;
    }

    @Override
    public List<UserVocabulary> getVocabularyList(Long userId) {
        return vocabularyMapper.selectList(
                new LambdaQueryWrapper<UserVocabulary>()
                        .eq(UserVocabulary::getUserId, userId)
                        .orderByDesc(UserVocabulary::getCreatedAt));
    }
}
