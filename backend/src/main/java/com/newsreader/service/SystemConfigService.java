package com.newsreader.service;

import com.newsreader.dto.ApiKeysDTO;

import java.util.Map;

public interface SystemConfigService {
    void saveApiKeys(ApiKeysDTO dto);
    Map<String, Object> getApiKeyStatus();
    String getNewsApiKey();
    String getDeepseekApiKey();
}
