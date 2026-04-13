package com.newsreader.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newsreader.dto.NewsFetchResultDTO;
import com.newsreader.entity.Article;

public interface ArticleService {
    Page<Article> getArticles(Integer pageNum, Integer pageSize, String category, String difficulty, String keyword);
    Article getById(Long id);
    NewsFetchResultDTO fetchAndSaveFromNewsAPI();
}
