package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.SysDictItem;
import com.workflow.mapper.SysDictItemMapper;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 字典缓存服务
 * <p>
 * 将字典项数据缓存到内存，提供快速的值/编码/标签转换查询。
 * 缓存结构：dictCode -> { itemValue -> SysDictItem }
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictCacheService {

    private final SysDictItemMapper dictItemMapper;

    /**
     * 缓存：dictCode -> { itemValue -> SysDictItem }
     */
    private volatile Map<String, Map<String, SysDictItem>> valueCache = new ConcurrentHashMap<>();

    /**
     * 缓存：dictCode -> { itemCode -> SysDictItem }
     */
    private volatile Map<String, Map<String, SysDictItem>> codeCache = new ConcurrentHashMap<>();

    /**
     * 系统启动时加载字典缓存
     */
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重新加载字典缓存
     */
    public void reload() {
        List<SysDictItem> items = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getStatus, SysDictItem.Status.ENABLED.getValue())
                        .orderByAsc(SysDictItem::getSort)
        );

        valueCache = items.stream()
                .collect(Collectors.groupingBy(
                        SysDictItem::getDictCode,
                        ConcurrentHashMap::new,
                        Collectors.toMap(SysDictItem::getItemValue, item -> item, (a, b) -> a)
                ));

        codeCache = items.stream()
                .collect(Collectors.groupingBy(
                        SysDictItem::getDictCode,
                        ConcurrentHashMap::new,
                        Collectors.toMap(SysDictItem::getItemCode, item -> item, (a, b) -> a)
                ));

        log.info("字典缓存加载完成，共 {} 个字典项", items.size());
    }

    // ==================== 值 -> 编码/标签 ====================

    /**
     * 根据字典编码和值，获取字典项
     */
    public SysDictItem getItem(String dictCode, String value) {
        return valueCache.getOrDefault(dictCode, Collections.emptyMap()).get(value);
    }

    /**
     * 根据字典编码和值，获取项编码
     */
    public String getCode(String dictCode, String value) {
        SysDictItem item = getItem(dictCode, value);
        return item != null ? item.getItemCode() : null;
    }

    /**
     * 根据字典编码和值，获取标签
     */
    public String getLabel(String dictCode, String value) {
        SysDictItem item = getItem(dictCode, value);
        return item != null ? item.getItemLabel() : null;
    }

    // ==================== 编码 -> 值/标签 ====================

    /**
     * 根据字典编码和项编码，获取字典项
     */
    public SysDictItem getItemByCode(String dictCode, String itemCode) {
        return codeCache.getOrDefault(dictCode, Collections.emptyMap()).get(itemCode);
    }

    /**
     * 根据字典编码和项编码，获取值
     */
    public String getValue(String dictCode, String itemCode) {
        SysDictItem item = getItemByCode(dictCode, itemCode);
        return item != null ? item.getItemValue() : null;
    }

    /**
     * 根据字典编码和项编码，获取标签
     */
    public String getLabelByCode(String dictCode, String itemCode) {
        SysDictItem item = getItemByCode(dictCode, itemCode);
        return item != null ? item.getItemLabel() : null;
    }

    // ==================== 批量查询 ====================

    /**
     * 获取某个字典下的所有字典项（按值索引）
     */
    public Map<String, SysDictItem> getItems(String dictCode) {
        return valueCache.getOrDefault(dictCode, Collections.emptyMap());
    }

    /**
     * 获取某个字典下的所有字典项列表
     */
    public List<SysDictItem> getItemList(String dictCode) {
        return valueCache.getOrDefault(dictCode, Collections.emptyMap())
                .values().stream()
                .collect(Collectors.toList());
    }
}
