package com.newsreader.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecommendedArticleDTO {
    private Long id;
    private String title;
    private String summary;
    private String source;
    private String category;
    private String difficulty;
    private String keywords;
    private LocalDateTime publishedAt;

    private Integer score;
    private List<String> reasonTags;
}
