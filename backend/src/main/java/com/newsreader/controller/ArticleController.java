package com.newsreader.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newsreader.common.Result;
import com.newsreader.dto.NewsFetchResultDTO;
import com.newsreader.entity.Article;
import com.newsreader.service.ArticleService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public Result<Page<Article>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword) {
        return Result.success(articleService.getArticles(pageNum, pageSize, category, difficulty, keyword));
    }

    @GetMapping("/{id}")
    public Result<Article> detail(@PathVariable Long id) {
        Article article = articleService.getById(id);
        if (article == null) return Result.fail(404, "文章不存在");
        return Result.success(article);
    }

    @PostMapping("/fetch")
    public Result<NewsFetchResultDTO> fetchNews() {
        NewsFetchResultDTO result = articleService.fetchAndSaveFromNewsAPI();
        return Result.success(result);
    }
}
