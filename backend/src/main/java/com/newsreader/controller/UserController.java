package com.newsreader.controller;

import com.newsreader.common.Result;
import com.newsreader.dto.LoginDTO;
import com.newsreader.dto.RegisterDTO;
import com.newsreader.entity.User;
import com.newsreader.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success("注册成功");
    }

    @GetMapping("/profile")
    public Result<User> getProfile(@AuthenticationPrincipal Long userId) {
        return Result.success(userService.getById(userId));
    }

    @PutMapping("/profile")
    public Result<?> updateProfile(@AuthenticationPrincipal Long userId,
                                   @RequestBody User user) {
        userService.updateProfile(userId, user);
        return Result.success("更新成功");
    }
}
