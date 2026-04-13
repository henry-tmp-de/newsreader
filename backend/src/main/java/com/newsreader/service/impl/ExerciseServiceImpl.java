package com.newsreader.service.impl;

import com.newsreader.entity.Article;
import com.newsreader.entity.Exercise;
import com.newsreader.entity.LearningRecord;
import com.newsreader.mapper.ArticleMapper;
import com.newsreader.mapper.ExerciseMapper;
import com.newsreader.mapper.LearningRecordMapper;
import com.newsreader.service.AIService;
import com.newsreader.service.ExerciseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseMapper exerciseMapper;
    private final ArticleMapper articleMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final AIService aiService;

    public ExerciseServiceImpl(ExerciseMapper exerciseMapper, ArticleMapper articleMapper,
                               LearningRecordMapper learningRecordMapper, AIService aiService) {
        this.exerciseMapper = exerciseMapper;
        this.articleMapper = articleMapper;
        this.learningRecordMapper = learningRecordMapper;
        this.aiService = aiService;
    }

    @Override
    public List<Exercise> getByArticleId(Long articleId) {
        return exerciseMapper.findByArticleId(articleId);
    }

    @Override
    public List<Exercise> generateForArticle(Long articleId, String userLevel) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) throw new RuntimeException("文章不存在");

        List<Map<String, Object>> generated = aiService.generateExercises(
                article.getContent(), userLevel != null ? userLevel : "INTERMEDIATE", 5);

        for (Map<String, Object> item : generated) {
            Exercise ex = new Exercise();
            ex.setArticleId(articleId);
            ex.setType((String) item.get("type"));
            ex.setQuestion((String) item.get("question"));
            ex.setOptions((String) item.get("options"));
            ex.setCorrectAnswer((String) item.get("correctAnswer"));
            ex.setExplanation((String) item.get("explanation"));
            exerciseMapper.insert(ex);
        }

        return exerciseMapper.findByArticleId(articleId);
    }

    @Override
    public String submitAnswer(Long exerciseId, String userAnswer, Long userId) {
        Exercise exercise = exerciseMapper.selectById(exerciseId);
        if (exercise == null) throw new RuntimeException("题目不存在");

        boolean correct = exercise.getCorrectAnswer().equalsIgnoreCase(userAnswer.trim());

        LearningRecord record = new LearningRecord();
        record.setUserId(userId);
        record.setArticleId(exercise.getArticleId());
        record.setExerciseId(exerciseId);
        record.setActionType("EXERCISE_DONE");
        record.setCorrect(correct);
        record.setScore(correct ? 10 : 0);
        learningRecordMapper.insert(record);

        if (!correct) {
            // 返回AI解析
            return aiService.evaluateAnswer(exercise.getQuestion(), userAnswer,
                    exercise.getCorrectAnswer(), exercise.getExplanation());
        }
        return "Correct! " + exercise.getExplanation();
    }
}
