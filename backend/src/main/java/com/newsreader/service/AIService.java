package com.newsreader.service;

import java.util.List;
import java.util.Map;

public interface AIService {
    /** 生成文章摘要 */
    String generateSummary(String articleContent);

    /** 提取关键词 */
    String extractKeywords(String articleContent);

    /** 评估文章难度 */
    String assessDifficulty(String articleContent);

    /** 查询单词释义 */
    Map<String, String> lookupWord(String word, String context);

    /** 生成文章练习题 */
    List<Map<String, Object>> generateExercises(String articleContent, String level, Integer count);

    /** 评估答案并给出解释 */
    String evaluateAnswer(String question, String userAnswer, String correctAnswer, String context);
}
