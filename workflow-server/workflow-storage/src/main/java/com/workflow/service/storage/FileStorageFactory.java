package com.workflow.service.storage;

import com.workflow.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件存储策略工厂
 * 根据配置选择对应的存储策略
 */
@Component
@RequiredArgsConstructor
public class FileStorageFactory {

    private final FileStorageProperties properties;
    private final List<FileStorageStrategy> strategies;

    /**
     * 获取当前配置的存储策略
     */
    public FileStorageStrategy getStrategy() {
        String type = properties.getType();
        for (FileStorageStrategy strategy : strategies) {
            if (strategy.getStorageType().equalsIgnoreCase(type)) {
                return strategy;
            }
        }
        throw new IllegalStateException("不支持的文件存储类型: " + type);
    }
}
