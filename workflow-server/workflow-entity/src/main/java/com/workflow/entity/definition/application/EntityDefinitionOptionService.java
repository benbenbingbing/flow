package com.workflow.entity.definition.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.api.request.EntityDefinitionOptionResolveRequest;
import com.workflow.entity.definition.api.response.EntityDefinitionOptionDTO;
import com.workflow.entity.definition.api.response.EntityDefinitionQueryDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 为通用实体选择器提供轻量分页查询和已选值回显。
 */
@Service
@RequiredArgsConstructor
public class EntityDefinitionOptionService {

    private final EntityDefinitionMapper entityMapper;

    @Transactional(readOnly = true)
    public PageResult<EntityDefinitionOptionDTO> findPage(EntityDefinitionQueryDTO query) {
        EntityDefinitionQueryDTO safeQuery = query == null ? new EntityDefinitionQueryDTO() : query;
        Page<EntityDefinition> page = new Page<>(
                positiveOrDefault(safeQuery.getPageNum(), 1),
                positiveOrDefault(safeQuery.getPageSize(), 10));
        Page<EntityDefinition> resultPage = entityMapper.selectPage(
                page,
                EntityDefinitionQueryBuilder.build(safeQuery));
        List<EntityDefinitionOptionDTO> records = resultPage.getRecords().stream()
                .map(this::toOption)
                .toList();
        return new PageResult<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    @Transactional(readOnly = true)
    public List<EntityDefinitionOptionDTO> resolve(EntityDefinitionOptionResolveRequest request) {
        List<String> ids = normalizeValues(request == null ? null : request.getIds());
        List<String> codes = normalizeValues(request == null ? null : request.getEntityCodes());
        if (ids.isEmpty() && codes.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<EntityDefinition> wrapper = Wrappers.<EntityDefinition>lambdaQuery()
                .and(nested -> {
                    if (!ids.isEmpty()) {
                        nested.in(EntityDefinition::getId, ids);
                    }
                    if (!codes.isEmpty()) {
                        nested.or(!ids.isEmpty()).in(EntityDefinition::getEntityCode, codes);
                    }
                });
        List<EntityDefinitionOptionDTO> options = entityMapper.selectList(wrapper).stream()
                .map(this::toOption)
                .toList();
        Map<String, EntityDefinitionOptionDTO> byId = indexBy(options, EntityDefinitionOptionDTO::getId);
        Map<String, EntityDefinitionOptionDTO> byCode = indexBy(
                options,
                EntityDefinitionOptionDTO::getEntityCode);

        LinkedHashMap<String, EntityDefinitionOptionDTO> ordered = new LinkedHashMap<>();
        ids.forEach(value -> addResolved(ordered, byId.get(normalizeKey(value))));
        codes.forEach(value -> addResolved(ordered, byCode.get(normalizeKey(value))));
        return new ArrayList<>(ordered.values());
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .limit(500)
                .toList();
    }

    private Map<String, EntityDefinitionOptionDTO> indexBy(
            List<EntityDefinitionOptionDTO> options,
            java.util.function.Function<EntityDefinitionOptionDTO, String> keyExtractor) {
        return options.stream()
                .filter(item -> StringUtils.isNotBlank(keyExtractor.apply(item)))
                .collect(Collectors.toMap(
                        item -> normalizeKey(keyExtractor.apply(item)),
                        item -> item,
                        (left, right) -> left));
    }

    private void addResolved(
            Map<String, EntityDefinitionOptionDTO> ordered,
            EntityDefinitionOptionDTO option) {
        if (option != null) {
            ordered.put(option.getId(), option);
        }
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private EntityDefinitionOptionDTO toOption(EntityDefinition entity) {
        EntityDefinitionOptionDTO option = new EntityDefinitionOptionDTO();
        option.setId(entity.getId());
        option.setEntityCode(entity.getEntityCode());
        option.setEntityName(entity.getEntityName());
        option.setLifecycleMode(lifecycleMode(entity));
        option.setStorageMode(entity.getStorageMode() == null
                ? EntityDefinition.StorageMode.DYNAMIC
                : entity.getStorageMode());
        option.setStatus(entity.getStatus());
        return option;
    }

    private EntityDefinition.LifecycleMode lifecycleMode(EntityDefinition entity) {
        if (entity.getLifecycleMode() != null) {
            return entity.getLifecycleMode();
        }
        return StringUtils.isNotBlank(entity.getProcessDefinitionId())
                ? EntityDefinition.LifecycleMode.WORKFLOW
                : EntityDefinition.LifecycleMode.STANDALONE;
    }
}
