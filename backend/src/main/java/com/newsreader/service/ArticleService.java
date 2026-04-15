package com.newsreader.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newsreader.dto.NewsFetchResultDTO;
import com.newsreader.entity.Article;

import java.time.LocalDateTime;
import java.util.List;

public interface ArticleService {
    Page<Article> getArticles(Integer pageNum, Integer pageSize, String category, String difficulty, String keyword);
    Article getById(Long id);
    NewsFetchResultDTO fetchAndSaveFromNewsAPI();
    NewsFetchResultDTO fetchAndSaveFromNewsAPI(List<String> categories, Integer pageSize);
    NewsFetchResultDTO fetchAndSaveFromNewsAPI(List<String> categories, Integer pageSize, LocalDateTime fromDate, LocalDateTime toDate, boolean useDateRange);
    int deleteArticleById(Long id);
    int deleteArticlesByIds(List<Long> ids);
    int clearAllArticles();
}
