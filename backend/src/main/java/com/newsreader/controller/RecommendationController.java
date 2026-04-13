package com.newsreader.controller;

import com.newsreader.common.Result;
import com.newsreader.dto.RecommendedArticleDTO;
import com.newsreader.dto.UserProfileDTO;
import com.newsreader.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/profile")
    public Result<UserProfileDTO> profile(@AuthenticationPrincipal Long userId) {
        return Result.success(recommendationService.buildUserProfile(userId));
    }

    @GetMapping("/articles")
    public Result<List<RecommendedArticleDTO>> articles(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "12") Integer size) {
        return Result.success(recommendationService.recommendArticles(userId, size));
    }

    @PutMapping("/interests")
    public Result<?> updateInterests(@AuthenticationPrincipal Long userId,
                                     @RequestBody Map<String, Object> body) {
        List<String> interests = body.get("interests") instanceof List ?
                ((List<?>) body.get("interests")).stream().map(String::valueOf).toList() : List.of();
        recommendationService.updateInterests(userId, interests);
        return Result.success("兴趣偏好已更新");
    }

    @PostMapping("/feedback")
    public Result<?> feedback(@AuthenticationPrincipal Long userId,
                              @RequestBody Map<String, Object> body) {
        Long articleId = body.get("articleId") == null ? null : Long.parseLong(body.get("articleId").toString());
        String feedbackType = body.get("feedbackType") == null ? "" : body.get("feedbackType").toString();
        recommendationService.recordArticleFeedback(userId, articleId, feedbackType);
        return Result.success("反馈已记录");
    }
}
