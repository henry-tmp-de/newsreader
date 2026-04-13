package com.newsreader.service;

import com.newsreader.dto.LoginDTO;
import com.newsreader.dto.RegisterDTO;
import com.newsreader.entity.User;

import java.util.Map;

public interface UserService {
    Map<String, Object> login(LoginDTO dto);
    void register(RegisterDTO dto);
    User getById(Long userId);
    void updateProfile(Long userId, User user);
}
