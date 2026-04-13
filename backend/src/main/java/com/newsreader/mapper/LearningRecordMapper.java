package com.newsreader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newsreader.entity.LearningRecord;
import org.apache.ibatis.annotations.Mapper;
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
}
