package com.newsreader.controller;

import com.newsreader.common.Result;
import com.newsreader.entity.Exercise;
import com.newsreader.entity.User;
import com.newsreader.service.ExerciseService;
import com.newsreader.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final UserService userService;

    public ExerciseController(ExerciseService exerciseService, UserService userService) {
        this.exerciseService = exerciseService;
        this.userService = userService;
    }

    @GetMapping("/article/{articleId}")
    public Result<List<Exercise>> getByArticle(@PathVariable Long articleId) {
        return Result.success(exerciseService.getByArticleId(articleId));
    }

    @PostMapping("/generate/{articleId}")
    public Result<List<Exercise>> generate(@PathVariable Long articleId,
                                           @AuthenticationPrincipal Long userId) {
        User user = userService.getById(userId);
        String level = user != null ? user.getLevel() : "INTERMEDIATE";
        return Result.success(exerciseService.generateForArticle(articleId, level));
    }

    @PostMapping("/submit")
    public Result<String> submit(@RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal Long userId) {
        Long exerciseId = Long.parseLong(body.get("exerciseId").toString());
        String answer = body.get("answer").toString();
        String feedback = exerciseService.submitAnswer(exerciseId, answer, userId);
        return Result.success(feedback);
    }
}
