package com.newsreader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newsreader.entity.LearningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    @Select("SELECT COUNT(*) as total, SUM(CASE WHEN correct = 1 THEN 1 ELSE 0 END) as correct_count " +
            "FROM learning_records WHERE user_id = #{userId} AND action_type = 'EXERCISE_DONE'")
    Map<String, Object> getExerciseStats(Long userId);

    @Select("SELECT COUNT(DISTINCT article_id) FROM learning_records WHERE user_id = #{userId} AND action_type = 'ARTICLE_COMPLETE'")
    Integer getReadArticleCount(Long userId);

    @Select("SELECT COUNT(DISTINCT article_id) FROM learning_records WHERE user_id = #{userId} AND action_type = 'READ'")
    Integer getCompletedArticleCount(Long userId);

    @Select("SELECT COALESCE(ROUND(AVG(duration)), 0) FROM learning_records WHERE user_id = #{userId} AND action_type = 'READ' AND duration IS NOT NULL AND duration > 0")
    Integer getAvgReadDuration(Long userId);

    @Select("SELECT COUNT(*) FROM learning_records WHERE user_id = #{userId} AND action_type = 'WORD_LOOKUP'")
    Integer getLookupCount(Long userId);

    @Select("SELECT COUNT(DISTINCT DATE(created_at)) FROM learning_records WHERE user_id = #{userId} AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)")
    Integer getActiveDays14(Long userId);

    @Select("SELECT article_id FROM learning_records WHERE user_id = #{userId} AND article_id IS NOT NULL GROUP BY article_id ORDER BY MAX(created_at) DESC LIMIT #{limit}")
    List<Long> getRecentInteractedArticleIds(@Param("userId") Long userId, @Param("limit") Integer limit);

    @Select("SELECT article_id FROM learning_records WHERE user_id = #{userId} AND action_type = 'ARTICLE_DISLIKE' AND article_id IS NOT NULL GROUP BY article_id")
    List<Long> getDislikedArticleIds(Long userId);

    @Select("SELECT a.category AS category, SUM(CASE WHEN lr.action_type = 'ARTICLE_LIKE' THEN 1 ELSE -1 END) AS score " +
            "FROM learning_records lr JOIN articles a ON lr.article_id = a.id " +
            "WHERE lr.user_id = #{userId} AND lr.action_type IN ('ARTICLE_LIKE', 'ARTICLE_DISLIKE') AND a.category IS NOT NULL " +
            "GROUP BY a.category")
    List<Map<String, Object>> getCategoryPreferenceScores(Long userId);
}
