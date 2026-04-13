package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.newsreader.dto.LoginDTO;
import com.newsreader.dto.RegisterDTO;
import com.newsreader.entity.User;
import com.newsreader.mapper.UserMapper;
import com.newsreader.service.UserService;
import com.newsreader.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        String username = dto.getUsername() == null ? null : dto.getUsername().trim();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 兼容历史明文密码数据：登录成功后自动升级为 BCrypt
        boolean passwordMatched = passwordEncoder.matches(dto.getPassword(), user.getPassword());
        if (!passwordMatched && dto.getPassword().equals(user.getPassword())) {
            User patch = new User();
            patch.setId(user.getId());
            patch.setPassword(passwordEncoder.encode(dto.getPassword()));
            userMapper.updateById(patch);
            passwordMatched = true;
        }
        if (!passwordMatched) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        user.setPassword(null);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    @Override
    public void register(RegisterDTO dto) {
        String username = dto.getUsername() == null ? null : dto.getUsername().trim();
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setLevel(dto.getLevel() != null ? dto.getLevel() : "BEGINNER");
        user.setInterests(dto.getInterests());
        user.setDeleted(0);
        userMapper.insert(user);
    }

    @Override
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public void updateProfile(Long userId, User updateData) {
        updateData.setId(userId);
        updateData.setPassword(null); // 禁止通过此接口修改密码
        userMapper.updateById(updateData);
    }
}
