package com.newsreader.service;

import com.newsreader.entity.LearningRecord;
import com.newsreader.entity.UserVocabulary;

import java.util.List;
import java.util.Map;

public interface LearningService {
    void recordAction(Long userId, Long articleId, String actionType, Integer duration);
    Map<String, Object> getStats(Long userId);
    Map<String, String> lookupWord(String word, String context, Long userId);
    List<UserVocabulary> getVocabularyList(Long userId);
}
