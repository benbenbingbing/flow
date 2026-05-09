package com.workflow.service.listfield;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 列表字段数据提供者注册中心
 */
@Slf4j
@Component
public class ListFieldDataProviderRegistry {

    private final Map<String, ListFieldDataProvider> providers = new HashMap<>();

    public ListFieldDataProviderRegistry(List<ListFieldDataProvider> providerList) {
        for (ListFieldDataProvider provider : providerList) {
            String type = provider.getDataSourceType();
            providers.put(type, provider);
            log.info("注册列表字段数据提供者: type={}, class={}", type, provider.getClass().getSimpleName());
        }
    }

    /**
     * 获取指定类型的数据提供者
     */
    public ListFieldDataProvider getProvider(String dataSourceType) {
        return providers.get(dataSourceType);
    }

    /**
     * 是否支持该数据源类型
     */
    public boolean supports(String dataSourceType) {
        return providers.containsKey(dataSourceType);
    }
}
