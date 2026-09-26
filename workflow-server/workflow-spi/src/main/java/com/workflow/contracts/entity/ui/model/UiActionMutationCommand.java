package com.workflow.contracts.entity.ui.model;

import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命令计划提供者可声明的最小实体变更意图。
 *
 * <p>operationId、幂等键、调用来源和审计信息均由平台生成，故不属于此契约。</p>
 *
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param operationType 操作类型标识，决定后续界面动作变更命令采用的处理分支
 * @param data 数据，后续用于处理界面动作变更命令并传递处理结果
 */
public record UiActionMutationCommand(
        String entityCode,
        String recordId,
        EntityMutationOperationType operationType,
        Map<String, Object> data) {

    /**
     * 初始化界面动作变更命令，保存构造参数供后续方法使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param operationType 操作类型标识，决定后续界面动作变更采用的处理分支
     * @param data 数据，后续用于初始化界面动作变更并传递处理结果
     */
    public UiActionMutationCommand {
        entityCode = entityCode == null ? null : entityCode.trim();
        recordId = recordId == null ? null : recordId.trim();
        data = data == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
