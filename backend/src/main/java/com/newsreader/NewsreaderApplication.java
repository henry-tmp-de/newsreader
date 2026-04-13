package com.newsreader;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.newsreader.mapper")
@EnableScheduling
@EnableAsync
public class NewsreaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(NewsreaderApplication.class, args);
    }
}
