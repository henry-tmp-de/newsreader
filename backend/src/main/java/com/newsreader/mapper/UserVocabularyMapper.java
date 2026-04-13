package com.newsreader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newsreader.entity.UserVocabulary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserVocabularyMapper extends BaseMapper<UserVocabulary> {

    @Select("SELECT * FROM user_vocabulary WHERE user_id = #{userId} ORDER BY next_review_at ASC LIMIT #{limit}")
    List<UserVocabulary> findReviewWords(Long userId, Integer limit);
}
