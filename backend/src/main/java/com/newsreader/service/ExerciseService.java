package com.newsreader.service;

import com.newsreader.entity.Exercise;

import java.util.List;

public interface ExerciseService {
    List<Exercise> getByArticleId(Long articleId);
    List<Exercise> generateForArticle(Long articleId, String userLevel);
    String submitAnswer(Long exerciseId, String userAnswer, Long userId);
}
