package com.newsreader.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("exercises")
public class Exercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private String question;

    /** JSON格式: ["A. ...", "B. ...", "C. ...", "D. ..."] */
    private String options;

    private String correctAnswer;

    private String explanation;

    /** 题型: VOCABULARY, COMPREHENSION, GRAMMAR, TRANSLATION */
    private String type;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
