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

    /** 文件存储配置属性 */
    private final FileStorageProperties properties;
    /** Spring 注入的全部存储策略实现列表 */
    private final List<FileStorageStrategy> strategies;

    /**
     * 获取当前配置的存储策略。
     *
     * @return 与配置 type 匹配的存储策略
     * @throws IllegalStateException 当配置的存储类型无对应实现时抛出
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
