package com.newsreader.dto;

import lombok.Data;

@Data
public class NewsFetchResultDTO {
    private int fetched;
    private int inserted;
    private int duplicated;
    private int duplicatedInTask;
    private int skippedNoContent;
    private int failed;
    private int enqueuedForEnrichment;
    private int purgedInvalid;
    private int stagnationBreaks;
    private String note;
}
