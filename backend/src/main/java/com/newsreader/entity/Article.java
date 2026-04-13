package com.newsreader.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("articles")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    @TableField("`content`")
    private String content;

    private String summary;

    private String source;

    private String url;

    private String author;

    private String category;

    /** 难度: EASY, MEDIUM, HARD */
    private String difficulty;

    /** 关键词，逗号分隔 */
    private String keywords;

    private LocalDateTime publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
