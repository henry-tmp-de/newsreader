package com.newsreader.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newsreader.common.Result;
import com.newsreader.dto.NewsFetchResultDTO;
import com.newsreader.entity.Article;
import com.newsreader.service.ArticleService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @PostMapping("/fetch/custom")
    public Result<NewsFetchResultDTO> fetchNewsCustom(@RequestBody Map<String, Object> body) {
        List<String> categories = body.get("categories") instanceof List ?
                ((List<?>) body.get("categories")).stream().map(String::valueOf).toList() : null;
        Integer pageSize = body.get("pageSize") != null ?
                Integer.parseInt(body.get("pageSize").toString()) : 10;

        String fromDateRaw = body.get("fromDate") == null ? null : body.get("fromDate").toString();
        String toDateRaw = body.get("toDate") == null ? null : body.get("toDate").toString();
        boolean useDateRange = body.get("useDateRange") != null && Boolean.parseBoolean(body.get("useDateRange").toString());

        LocalDateTime fromDate = parseDateTime(fromDateRaw);
        LocalDateTime toDate = parseDateTime(toDateRaw);
        if (useDateRange && (fromDate == null || toDate == null)) {
            return Result.fail(400, "请填写完整的开始和结束时间");
        }
        if (useDateRange && fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return Result.fail(400, "日期范围无效：开始时间不能晚于结束时间");
        }

        NewsFetchResultDTO result = articleService.fetchAndSaveFromNewsAPI(categories, pageSize, fromDate, toDate, useDateRange);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteOne(@PathVariable Long id) {
        int affected = articleService.deleteArticleById(id);
        return affected > 0 ? Result.success("删除成功") : Result.fail(404, "文章不存在");
    }

    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestBody Map<String, Object> body) {
        List<Long> ids = parseIds(body);
        if (ids.isEmpty()) {
            return Result.fail(400, "请选择要删除的新闻");
        }
        int affected = articleService.deleteArticlesByIds(ids);
        return Result.success("已删除 " + affected + " 条新闻");
    }

    @PostMapping("/batch-delete")
    public Result<?> deleteBatchPost(@RequestBody Map<String, Object> body) {
        List<Long> ids = parseIds(body);
        if (ids.isEmpty()) {
            return Result.fail(400, "请选择要删除的新闻");
        }
        int affected = articleService.deleteArticlesByIds(ids);
        return Result.success("已删除 " + affected + " 条新闻");
    }

    @DeleteMapping("/clear")
    public Result<?> clearAll() {
        int affected = articleService.clearAllArticles();
        return Result.success("新闻库已清空，共删除 " + affected + " 条");
    }

    @PostMapping("/clear-all")
    public Result<?> clearAllPost() {
        int affected = articleService.clearAllArticles();
        return Result.success("新闻库已清空，共删除 " + affected + " 条");
    }

    private List<Long> parseIds(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List)) {
            return List.of();
        }
        return ((List<?>) body.get("ids")).stream()
                .map(this::tryParseLong)
                .filter(v -> v != null && v > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    private Long tryParseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
