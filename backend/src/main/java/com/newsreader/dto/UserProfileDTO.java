package com.newsreader.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileDTO {
    private Long userId;
    private String level;
    private Integer abilityScore;
    private String targetDifficulty;
    private List<String> interests;

    private Integer readArticles;
    private Integer completedArticles;
    private Integer avgReadDurationSec;
    private Integer exerciseAccuracy;
    private Integer lookupCount;
    private Integer activeDays14;
}
