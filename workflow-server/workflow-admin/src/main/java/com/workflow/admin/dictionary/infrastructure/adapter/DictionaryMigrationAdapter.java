package com.workflow.admin.dictionary.infrastructure.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.dictionary.port.DictionaryMigrationPort;
import com.workflow.admin.dictionary.application.DictCacheService;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 字典配置落库由所属领域完成；迁移编排只传递现有便携快照协议。 */
@Component
@RequiredArgsConstructor
public class DictionaryMigrationAdapter implements DictionaryMigrationPort {
    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;
    private final DictCacheService cache;
    private final ObjectMapper objectMapper;

    /** 非破坏性合并字典项，保留目标环境独有项；参与调用方事务，父项解析失败整体回滚。 */
    @Override
    @Transactional
    public void apply(String dictionaryCode, Map<String, Object> snapshot) {
        SysDict incoming = convert(
                mapValue(snapshot.get("definition")),
                SysDict.class);
        String dictCode = text(
                incoming.getDictCode(), dictionaryCode);
        SysDict dictionary = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, dictCode));
        LocalDateTime now = LocalDateTime.now();
        if (dictionary == null) {
            dictionary = incoming;
            dictionary.setId(null);
            dictionary.setDictCode(dictCode);
            dictionary.setStatus(
                    StringUtils.hasText(dictionary.getStatus())
                            ? dictionary.getStatus()
                            : SysDict.Status.ENABLED.getValue());
            dictionary.setDeleted(0);
            dictionary.setCreateTime(now);
            dictionary.setUpdateTime(now);
            dictMapper.insert(dictionary);
        } else {
            dictionary.setDictName(incoming.getDictName());
            dictionary.setDescription(incoming.getDescription());
            dictionary.setStatus(incoming.getStatus());
            dictionary.setSort(incoming.getSort());
            dictionary.setUpdateTime(now);
            dictMapper.updateById(dictionary);
        }

        Map<String, SysDictItem> targetItems = dictItemMapper
                .selectAllByDictId(dictionary.getId())
                .stream()
                .filter(value -> value.getDeleted() == null
                        || value.getDeleted() == 0)
                .collect(java.util.stream.Collectors.toMap(
                        SysDictItem::getItemCode,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> incomingItems =
                mapList(snapshot.get("items"));
        for (Map<String, Object> value : incomingItems) {
            SysDictItem incomingItem = convert(
                    value, SysDictItem.class);
            if (!StringUtils.hasText(
                    incomingItem.getItemCode())) {
                throw new IllegalStateException(
                        "迁移字典项缺少 itemCode: " + dictCode);
            }
            SysDictItem target = targetItems.get(
                    incomingItem.getItemCode());
            String targetId =
                    target == null ? null : target.getId();
            LocalDateTime createdAt = target == null
                    ? now : target.getCreateTime();
            incomingItem.setId(targetId);
            incomingItem.setDictId(dictionary.getId());
            incomingItem.setDictCode(dictCode);
            incomingItem.setParentId("0");
            incomingItem.setDeleted(0);
            incomingItem.setCreateTime(createdAt);
            incomingItem.setUpdateTime(now);
            if (target == null) {
                dictItemMapper.insert(incomingItem);
            } else {
                dictItemMapper.updateById(incomingItem);
            }
            targetItems.put(
                    incomingItem.getItemCode(), incomingItem);
        }

        // 首轮写入取得目标主键后，再解析父项编码，避免依赖源环境 parentId。
        for (Map<String, Object> value : incomingItems) {
            String itemCode = text(
                    value.get("itemCode"), null);
            String parentItemCode = text(
                    value.get("parentItemCode"), null);
            SysDictItem target = targetItems.get(itemCode);
            SysDictItem parent =
                    StringUtils.hasText(parentItemCode)
                            ? targetItems.get(parentItemCode)
                            : null;
            if (StringUtils.hasText(parentItemCode)
                    && parent == null) {
                throw new IllegalStateException(
                        "迁移字典项父节点不存在: "
                                + dictCode + "." + parentItemCode);
            }
            target.setParentId(
                    parent == null ? "0" : parent.getId());
            target.setUpdateTime(now);
            dictItemMapper.updateById(target);
        }
    }

    /** 回滚新建资产时停用字典，保留数据与原有时间戳更新语义。 */
    @Override
    @Transactional
    public boolean disable(String dictionaryCode) {
        SysDict dictionary = dictMapper.selectOne(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictCode, dictionaryCode));
        if (dictionary != null) {
            dictionary.setStatus(SysDict.Status.DISABLED.getValue());
            dictionary.setUpdateTime(LocalDateTime.now());
            dictMapper.updateById(dictionary);
            cache.reload();
            return true;
        }
        return false;
    }

    /** 批量应用后由编排统一刷新，避免每个字典都全量重建缓存。 */
    @Override
    public void refreshCache() {
        cache.reload();
    }

    // 便携快照允许未知属性，兼容现存包；这些转换只在迁移协议入口使用。
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?>) {
                result.add(mapValue(item));
            }
        }
        return result;
    }

    private <T> T convert(Map<String, Object> value, Class<T> type) {
        ObjectMapper tolerant = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return tolerant.convertValue(value, type);
    }

    private String text(Object value, String fallback) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return fallback;
        }
        return String.valueOf(value);
    }
}
