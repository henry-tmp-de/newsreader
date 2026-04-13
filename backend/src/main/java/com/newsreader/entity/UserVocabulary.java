package com.newsreader.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_vocabulary")
public class UserVocabulary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String word;

    /** WORD / SENTENCE */
    private String entryType;

    private String definition;

    private String chinese;

    private String example;

    /** 选中文本所在上下文 */
    private String contextText;

    /** 掌握程度 0-5 */
    private Integer masteryLevel;

    private Integer reviewCount;

    private LocalDateTime nextReviewAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
