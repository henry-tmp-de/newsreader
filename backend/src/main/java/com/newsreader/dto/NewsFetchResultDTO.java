package com.newsreader.dto;

import lombok.Data;

@Data
public class NewsFetchResultDTO {
    private int fetched;
    private int inserted;
    private int duplicated;
    private int skippedNoContent;
    private int failed;
    private int enqueuedForEnrichment;
}
