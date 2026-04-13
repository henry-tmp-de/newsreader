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

    /** 查询词或句子（自动返回中英解释） */
    Map<String, String> lookupText(String text, String context, String type);

    /** 生成文章练习题 */
    List<Map<String, Object>> generateExercises(String articleContent, String level, Integer count);

    /** 评估答案并给出解释 */
    String evaluateAnswer(String question, String userAnswer, String correctAnswer, String context);

    /** 基于文章内容进行问答 */
    String chatAboutArticle(String articleContent, String question, java.util.List<Map<String, String>> history);
}
