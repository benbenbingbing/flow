package com.workflow.entity.data.application;

import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 实体字段附件项配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFieldFileItemService {

    private static final Pattern ITEM_KEY =
            Pattern.compile("afi_[A-Za-z0-9_-]{1,60}");

    private final EntityFieldFileItemMapper fileItemMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据字段ID查询附件项列表
     *
     * @param fieldId 字段ID，后续用于查询字段ID时定位或关联目标
     * @return 实体字段文件条目集合，供调用方遍历或展示
     */
    public List<EntityFieldFileItem> findByFieldId(String fieldId) {
        return fileItemMapper.findByFieldId(fieldId);
    }

    /**
     * 批量保存附件项（先删除旧数据，再插入新数据）
     *
     * @param fieldId 字段ID，后续用于保存文件条目时定位或关联目标
     * @param items 条目，供本方法保存文件条目时使用
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFileItems(String fieldId, List<EntityFieldFileItem> items) {
        List<EntityFieldFileItem> existingItems =
                fileItemMapper.findByFieldId(fieldId);
        if (existingItems == null) {
            existingItems = List.of();
        }
        Map<String, EntityFieldFileItem> existingById = new LinkedHashMap<>();
        Map<String, EntityFieldFileItem> existingByKey = new LinkedHashMap<>();
        Map<String, EntityFieldFileItem> existingByName = new LinkedHashMap<>();
        Set<String> ambiguousNames = new LinkedHashSet<>();
        for (EntityFieldFileItem existing : existingItems) {
            if (StringUtils.hasText(existing.getId())) {
                existingById.put(existing.getId(), existing);
            }
            if (StringUtils.hasText(existing.getItemKey())) {
                existingByKey.put(existing.getItemKey(), existing);
            }
            if (StringUtils.hasText(existing.getItemName())) {
                indexExistingName(
                        existingByName,
                        ambiguousNames,
                        existing.getItemName(),
                        existing);
            }
            for (String alias : aliases(existing.getNameAliases())) {
                indexExistingName(
                        existingByName,
                        ambiguousNames,
                        alias,
                        existing);
            }
        }

        List<EntityFieldFileItem> normalized = new ArrayList<>();
        Set<String> itemKeys = new LinkedHashSet<>();
        Set<String> itemNames = new LinkedHashSet<>();
        Map<String, String> identityOwners = new LinkedHashMap<>();
        if (items != null) {
            for (EntityFieldFileItem item : items) {
                if (item == null || !StringUtils.hasText(item.getItemName())) {
                    throw new IllegalArgumentException("附件项名称不能为空");
                }
                String itemName = item.getItemName().trim();
                if (!itemNames.add(itemName)) {
                    throw new IllegalArgumentException(
                            "附件项名称不能重复: " + itemName);
                }
                EntityFieldFileItem existing = findExisting(
                        item,
                        existingById,
                        existingByKey,
                        existingByName);
                String itemKey = existing != null
                        && StringUtils.hasText(existing.getItemKey())
                        ? existing.getItemKey()
                        : item.getItemKey();
                if (!StringUtils.hasText(itemKey)) {
                    itemKey = newItemKey();
                }
                if (!ITEM_KEY.matcher(itemKey).matches()) {
                    throw new IllegalArgumentException(
                            "附件项业务标识不合法: " + itemKey);
                }
                if (!itemKeys.add(itemKey)) {
                    throw new IllegalArgumentException(
                            "附件项业务标识不能重复: " + itemKey);
                }
                Set<String> aliases = new LinkedHashSet<>();
                if (existing != null) {
                    if (StringUtils.hasText(existing.getItemName())
                            && !existing.getItemName().equals(itemName)) {
                        aliases.add(existing.getItemName());
                    }
                }
                aliases.addAll(aliases(item.getNameAliases()));
                if (existing != null) {
                    aliases.addAll(aliases(existing.getNameAliases()));
                    if (!StringUtils.hasText(item.getId())) {
                        item.setId(existing.getId());
                    }
                }
                aliases.remove(itemName);
                validateUniqueIdentities(
                        itemName,
                        aliases,
                        itemKey,
                        identityOwners);
                item.setItemName(itemName);
                item.setItemKey(itemKey);
                item.setNameAliases(writeAliases(aliases));
                normalized.add(item);
            }
        }

        fileItemMapper.deleteByFieldId(fieldId);

        if (!normalized.isEmpty()) {
            for (int i = 0; i < normalized.size(); i++) {
                EntityFieldFileItem item = normalized.get(i);
                item.setFieldId(fieldId);
                item.setSortOrder(i);
                if (item.getCreatedAt() == null) {
                    item.setCreatedAt(LocalDateTime.now());
                }
                item.setUpdatedAt(LocalDateTime.now());
                fileItemMapper.insert(item);
            }
        }
    }

    /**
     * 查询已有；查询结果供调用方展示或继续处理。
     *
     * @param item 条目，作为 {@code existingById.get} 的输入影响后续处理
     * @param existingById 已有ID，后续用于查询已有时定位或关联目标
     * @param existingByKey 已有键，后续用于授权校验、关联或幂等去重
     * @param existingByName 已有名称，后续用于查询已有时匹配或展示
     * @return 符合条件的实体字段文件条目结果，供调用方继续处理
     */
    private EntityFieldFileItem findExisting(
            EntityFieldFileItem item,
            Map<String, EntityFieldFileItem> existingById,
            Map<String, EntityFieldFileItem> existingByKey,
            Map<String, EntityFieldFileItem> existingByName) {
        if (StringUtils.hasText(item.getId())
                && existingById.containsKey(item.getId())) {
            return existingById.get(item.getId());
        }
        if (StringUtils.hasText(item.getItemKey())
                && existingByKey.containsKey(item.getItemKey())) {
            return existingByKey.get(item.getItemKey());
        }
        return StringUtils.hasText(item.getItemName())
                ? findExistingByName(item, existingByName)
                : null;
    }

    /**
     * 按名称查询实体字段文件条目；结果供后续展示或处理。
     *
     * @param item 条目，作为 {@code existingByName.get} 的输入影响后续处理
     * @param existingByName 已有名称，后续用于查询已有名称时匹配或展示
     * @return 符合条件的实体字段文件条目结果，供调用方继续处理
     */
    private EntityFieldFileItem findExistingByName(
            EntityFieldFileItem item,
            Map<String, EntityFieldFileItem> existingByName) {
        EntityFieldFileItem existing = existingByName.get(
                item.getItemName().trim());
        if (existing != null) {
            return existing;
        }
        for (String alias : aliases(item.getNameAliases())) {
            existing = existingByName.get(alias);
            if (existing != null) {
                return existing;
            }
        }
        return null;
    }

    /**
     * 处理索引已有名称，并将结果传给后续步骤。
     *
     * @param existingByName 已有名称，后续用于处理索引已有名称时匹配或展示
     * @param ambiguousNames {@code ambiguous}名称集合，供本方法处理索引已有名称时使用
     * @param value 待处理索引已有名称的原始输入，结果供调用方继续使用
     * @param item 条目，作为 {@code existingByName.putIfAbsent} 的输入影响后续处理
     */
    private void indexExistingName(
            Map<String, EntityFieldFileItem> existingByName,
            Set<String> ambiguousNames,
            String value,
            EntityFieldFileItem item) {
        String name = value == null ? "" : value.trim();
        if (!StringUtils.hasText(name) || ambiguousNames.contains(name)) {
            return;
        }
        EntityFieldFileItem previous = existingByName.putIfAbsent(
                name,
                item);
        if (previous != null && previous != item) {
            existingByName.remove(name);
            ambiguousNames.add(name);
        }
    }

    /**
     * 校验唯一{@code identities}；不满足约束时阻止后续处理。
     *
     * @param itemName 条目名称，后续用于校验唯一{@code identities}时匹配或展示
     * @param aliases {@code aliases}，作为 {@code identities.addAll} 的输入影响后续处理
     * @param itemKey 条目键，后续用于授权校验、关联或幂等去重
     * @param identityOwners 身份{@code owners}，供本方法校验唯一{@code identities}时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateUniqueIdentities(
            String itemName,
            Set<String> aliases,
            String itemKey,
            Map<String, String> identityOwners) {
        Set<String> identities = new LinkedHashSet<>();
        identities.add(itemName);
        identities.addAll(aliases);
        for (String identity : identities) {
            String owner = identityOwners.putIfAbsent(
                    identity,
                    itemKey);
            if (owner != null && !owner.equals(itemKey)) {
                throw new IllegalArgumentException(
                        "附件项名称与历史名称不能重复: " + identity);
            }
        }
    }

    /**
     * 整理{@code aliases}数据，供调用方遍历或继续处理。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 实体字段文件条目集合，供调用方遍历或展示
     */
    private Set<String> aliases(String document) {
        if (!StringUtils.hasText(document)) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> values = objectMapper.readValue(
                    document,
                    new TypeReference<List<String>>() {});
            return values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(
                            LinkedHashSet::new));
        } catch (Exception exception) {
            log.warn("附件项历史名称解析失败，将忽略无效配置: {}", document);
            return new LinkedHashSet<>();
        }
    }

    /**
     * 写入{@code aliases}；后续读取或执行将使用更新后的状态。
     *
     * @param aliases {@code aliases}，作为 {@code objectMapper.writeValueAsString} 的输入影响后续处理
     * @return 写入后的{@code aliases}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String writeAliases(Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(aliases);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "附件项历史名称序列化失败",
                    exception);
        }
    }

    /**
     * 生成新条目键文本，供后续匹配或展示。
     *
     * @return 处理后的新条目键文本，供调用方比较或展示
     */
    private String newItemKey() {
        return "afi_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 删除字段的所有附件项
     *
     * @param fieldId 字段ID，后续用于删除字段ID时定位或关联目标
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByFieldId(String fieldId) {
        fileItemMapper.deleteByFieldId(fieldId);
    }
}
