-- NewsReader 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS newsreader DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE newsreader;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '加密密码',
    email VARCHAR(100) COMMENT '邮箱',
    level VARCHAR(20) DEFAULT 'BEGINNER' COMMENT '英语水平: BEGINNER/INTERMEDIATE/ADVANCED',
    avatar VARCHAR(255) COMMENT '头像URL',
    interests VARCHAR(500) COMMENT '兴趣标签，逗号分隔',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文章表
CREATE TABLE IF NOT EXISTS articles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL COMMENT '文章标题',
    content TEXT NOT NULL COMMENT '文章正文',
    summary VARCHAR(1000) COMMENT 'AI生成摘要',
    source VARCHAR(100) COMMENT '来源站点',
    url VARCHAR(500) UNIQUE COMMENT '原文链接',
    author VARCHAR(100) COMMENT '作者',
    category VARCHAR(50) COMMENT '分类: technology/science/health/business/sports',
    difficulty VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '难度: EASY/MEDIUM/HARD',
    keywords VARCHAR(500) COMMENT 'AI提取关键词，逗号分隔',
    published_at DATETIME COMMENT '发布时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_difficulty (difficulty),
    INDEX idx_published (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 练习题表
CREATE TABLE IF NOT EXISTS exercises (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT NOT NULL COMMENT '关联文章ID',
    question TEXT NOT NULL COMMENT '题目',
    options JSON COMMENT '选项，JSON数组',
    correct_answer VARCHAR(10) COMMENT '正确答案',
    explanation TEXT COMMENT '解析',
    type VARCHAR(50) DEFAULT 'COMPREHENSION' COMMENT '题型: VOCABULARY/COMPREHENSION/GRAMMAR',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='练习题表';

-- 学习记录表
CREATE TABLE IF NOT EXISTS learning_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    article_id BIGINT COMMENT '文章ID',
    exercise_id BIGINT COMMENT '题目ID',
    action_type VARCHAR(50) COMMENT '行为类型: READ/EXERCISE_DONE/WORD_LOOKUP/ARTICLE_COMPLETE',
    score INT DEFAULT 0 COMMENT '得分',
    duration INT COMMENT '时长（秒）',
    correct TINYINT(1) COMMENT '是否正确',
    note TEXT COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_article (article_id),
    INDEX idx_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- 用户词汇表
CREATE TABLE IF NOT EXISTS user_vocabulary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    word VARCHAR(100) NOT NULL COMMENT '单词',
    definition TEXT COMMENT '释义（英文）',
    example TEXT COMMENT '例句',
    mastery_level INT DEFAULT 0 COMMENT '掌握程度 0-5',
    review_count INT DEFAULT 0 COMMENT '复习次数',
    next_review_at DATETIME COMMENT '下次复习时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_word (user_id, word),
    INDEX idx_review (user_id, next_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户词汇表';
