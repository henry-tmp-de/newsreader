package com.newsreader.controller;

import com.newsreader.common.Result;
import com.newsreader.dto.ApiKeysDTO;
import com.newsreader.service.SystemConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system/config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/api-keys")
    public Result<Map<String, Object>> getApiKeyStatus() {
        return Result.success(systemConfigService.getApiKeyStatus());
    }

    @PutMapping("/api-keys")
    public Result<?> saveApiKeys(@RequestBody ApiKeysDTO dto) {
        systemConfigService.saveApiKeys(dto);
        return Result.success("API Key 保存成功");
    }
}
