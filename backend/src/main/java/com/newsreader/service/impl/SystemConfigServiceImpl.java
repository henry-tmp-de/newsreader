package com.newsreader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.newsreader.dto.ApiKeysDTO;
import com.newsreader.entity.SystemConfig;
import com.newsreader.mapper.SystemConfigMapper;
import com.newsreader.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigServiceImpl.class);

    private static final String KEY_NEWS_API = "news_api_key";
    private static final String KEY_DEEPSEEK_API = "deepseek_api_key";

    private final SystemConfigMapper systemConfigMapper;

    @Value("${news-api.key:}")
    private String defaultNewsApiKey;

    @Value("${deepseek.api-key:}")
    private String defaultDeepseekApiKey;

    public SystemConfigServiceImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @Override
    public void saveApiKeys(ApiKeysDTO dto) {
        if (dto == null) {
            return;
        }
        if (StringUtils.hasText(dto.getNewsApiKey())) {
            upsert(KEY_NEWS_API, dto.getNewsApiKey().trim());
        }
        if (StringUtils.hasText(dto.getDeepseekApiKey())) {
            upsert(KEY_DEEPSEEK_API, dto.getDeepseekApiKey().trim());
        }
    }

    @Override
    public Map<String, Object> getApiKeyStatus() {
        Map<String, Object> result = new HashMap<>();
        String newsKey = getNewsApiKey();
        String deepseekKey = getDeepseekApiKey();
        result.put("hasNewsApiKey", StringUtils.hasText(newsKey) && !isPlaceholder(newsKey));
        result.put("hasDeepseekApiKey", StringUtils.hasText(deepseekKey) && !isPlaceholder(deepseekKey));
        return result;
    }

    @Override
    public String getNewsApiKey() {
        String fromDb = getConfig(KEY_NEWS_API);
        return StringUtils.hasText(fromDb) ? fromDb : defaultNewsApiKey;
    }

    @Override
    public String getDeepseekApiKey() {
        String fromDb = getConfig(KEY_DEEPSEEK_API);
        return StringUtils.hasText(fromDb) ? fromDb : defaultDeepseekApiKey;
    }

    private void upsert(String key, String value) {
        try {
            SystemConfig exists = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
            if (exists == null) {
                SystemConfig cfg = new SystemConfig();
                cfg.setConfigKey(key);
                cfg.setConfigValue(value);
                cfg.setUpdatedAt(LocalDateTime.now());
                systemConfigMapper.insert(cfg);
            } else {
                exists.setConfigValue(value);
                exists.setUpdatedAt(LocalDateTime.now());
                systemConfigMapper.updateById(exists);
            }
        } catch (Exception e) {
            throw new RuntimeException("保存配置失败，请先执行 init.sql 中的 system_config 建表语句", e);
        }
    }

    private String getConfig(String key) {
        try {
            SystemConfig cfg = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
            return cfg == null ? null : cfg.getConfigValue();
        } catch (Exception e) {
            log.warn("Read system config failed, fallback to application.yml. key={}", key);
            return null;
        }
    }

    private boolean isPlaceholder(String value) {
        String lower = value.toLowerCase();
        return lower.contains("your_newsapi_key")
                || lower.contains("your_deepseek_api_key")
                || lower.contains("your_openai_api_key");
    }
}
