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

    /**
     * 按筛选条件分页查询实体定义选项；结果供列表展示。
     *
     * @param query 查询，供本方法查询实体定义选项分页时使用
     * @return 符合条件的实体定义选项结果，供调用方继续处理
     */
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

    /**
     * 解析实体定义选项；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析实体定义选项
     * @return 实体定义选项集合，供调用方遍历或展示
     */
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

    /**
     * 规范化值集合；输出作为后续校验或处理的输入。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体定义选项集合，供调用方遍历或展示
     */
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

    /**
     * 整理索引数据，供调用方遍历或继续处理。
     *
     * @param options 选项，供本方法处理索引时使用
     * @param keyExtractor 键{@code extractor}，供本方法处理索引时使用
     * @return 索引键值结果，供调用方继续处理
     */
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

    /**
     * 添加已解析；结果供后续流程传递或持久化。
     *
     * @param ordered {@code ordered}，供本方法添加已解析时使用
     * @param option 选项，作为 {@code ordered.put} 的输入影响后续处理
     */
    private void addResolved(
            Map<String, EntityDefinitionOptionDTO> ordered,
            EntityDefinitionOptionDTO option) {
        if (option != null) {
            ordered.put(option.getId(), option);
        }
    }

    /**
     * 规范化键；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化键的原始输入，结果供调用方继续使用
     * @return 规范化后的键文本，供调用方比较或展示
     */
    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * 处理正数或默认，并将结果传给后续步骤。
     *
     * @param value 待处理正数或默认的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的正数或默认结果，供调用方继续处理
     */
    private int positiveOrDefault(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    /**
     * 转换为选项；输出作为后续校验或处理的输入。
     *
     * @param entity 实体，作为 {@code option.setId} 的输入影响后续处理
     * @return 转换为后的选项结果，供调用方继续处理
     */
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

    /**
     * 处理生命周期模式，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code StringUtils.isNotBlank} 的输入影响后续处理
     * @return 处理后的生命周期模式结果，供调用方继续处理
     */
    private EntityDefinition.LifecycleMode lifecycleMode(EntityDefinition entity) {
        if (entity.getLifecycleMode() != null) {
            return entity.getLifecycleMode();
        }
        return StringUtils.isNotBlank(entity.getProcessDefinitionId())
                ? EntityDefinition.LifecycleMode.WORKFLOW
                : EntityDefinition.LifecycleMode.STANDALONE;
    }
}
