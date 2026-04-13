package com.newsreader.service;

import com.newsreader.dto.RecommendedArticleDTO;
import com.newsreader.dto.UserProfileDTO;

import java.util.List;

public interface RecommendationService {
    UserProfileDTO buildUserProfile(Long userId);
    List<RecommendedArticleDTO> recommendArticles(Long userId, Integer size);
    void updateInterests(Long userId, List<String> interests);
    void recordArticleFeedback(Long userId, Long articleId, String feedbackType);
}
