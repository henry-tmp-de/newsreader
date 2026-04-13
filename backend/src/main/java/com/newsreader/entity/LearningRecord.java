package com.newsreader.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("learning_records")
public class LearningRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long articleId;

    private Long exerciseId;

    /** 行为类型: READ, EXERCISE_DONE, WORD_LOOKUP, ARTICLE_COMPLETE */
    private String actionType;

    private Integer score;

    /** 阅读时长（秒） */
    private Integer duration;

    private Boolean correct;

    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
