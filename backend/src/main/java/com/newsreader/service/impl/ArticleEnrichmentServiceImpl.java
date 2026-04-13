package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.newsreader.entity.Article;
import com.newsreader.mapper.ArticleMapper;
import com.newsreader.service.AIService;
import com.newsreader.service.ArticleEnrichmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ArticleEnrichmentServiceImpl implements ArticleEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ArticleEnrichmentServiceImpl.class);

    private final ArticleMapper articleMapper;
    private final AIService aiService;

    public ArticleEnrichmentServiceImpl(ArticleMapper articleMapper, AIService aiService) {
        this.articleMapper = articleMapper;
        this.aiService = aiService;
    }

    @Override
    @Async
    public void enrichPendingArticlesAsync() {
        List<Article> pending = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .and(w -> w.isNull(Article::getSummary)
                                .or().eq(Article::getSummary, "")
                                .or().isNull(Article::getKeywords)
                                .or().eq(Article::getKeywords, ""))
                        .orderByDesc(Article::getCreatedAt)
                        .last("LIMIT 20"));

        for (Article article : pending) {
            try {
                if (!StringUtils.hasText(article.getContent())) {
                    continue;
                }
                Article patch = new Article();
                patch.setId(article.getId());
                patch.setSummary(aiService.generateSummary(article.getContent()));
                patch.setKeywords(aiService.extractKeywords(article.getContent()));
                patch.setDifficulty(aiService.assessDifficulty(article.getContent()));
                articleMapper.updateById(patch);
            } catch (Exception e) {
                log.warn("Article enrichment failed, articleId={}", article.getId(), e);
            }
        }
    }
}
