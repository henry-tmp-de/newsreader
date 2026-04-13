package com.newsreader.controller;

import com.newsreader.common.Result;
import com.newsreader.entity.UserVocabulary;
import com.newsreader.service.LearningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping("/record")
    public Result<?> recordAction(@RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal Long userId) {
        Long articleId = body.get("articleId") != null ?
                Long.parseLong(body.get("articleId").toString()) : null;
        String actionType = (String) body.get("actionType");
        Integer duration = body.get("duration") != null ?
                Integer.parseInt(body.get("duration").toString()) : null;
        learningService.recordAction(userId, articleId, actionType, duration);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(@AuthenticationPrincipal Long userId) {
        return Result.success(learningService.getStats(userId));
    }

    @PostMapping("/word-lookup")
    public Result<Map<String, String>> lookupWord(@RequestBody Map<String, String> body,
                                                   @AuthenticationPrincipal Long userId) {
        String word = body.get("word");
        String context = body.getOrDefault("context", "");
        return Result.success(learningService.lookupWord(word, context, userId));
    }

    @GetMapping("/vocabulary")
    public Result<List<UserVocabulary>> getVocabulary(@AuthenticationPrincipal Long userId) {
        return Result.success(learningService.getVocabularyList(userId));
    }
}
