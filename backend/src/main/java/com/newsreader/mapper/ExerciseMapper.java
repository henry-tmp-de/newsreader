package com.newsreader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newsreader.entity.Exercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExerciseMapper extends BaseMapper<Exercise> {

    @Select("SELECT * FROM exercises WHERE article_id = #{articleId}")
    List<Exercise> findByArticleId(Long articleId);
}
