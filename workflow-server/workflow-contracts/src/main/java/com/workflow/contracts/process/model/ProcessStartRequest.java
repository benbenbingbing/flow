package com.workflow.contracts.process.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 实体模块发起流程时传递的稳定请求模型，不暴露实体模块 DTO 或持久化对象。
 *
 * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param code 实体记录的业务编号，由服务端写入同名流程变量，不使用实体类型编码代替
 * @param submitterId 流程提交人 ID，后续用于发起记录和权限判断
 * @param submitterName 流程提交人名称，后续用于发起记录和页面展示
 * @param processingStatus 处理状态标识，决定后续流程启动请求采用的处理分支
 * @param data 数据，后续用于处理流程启动请求并传递处理结果
 * @param variables 流程变量，后续传给流程引擎或规则求值器使用
 */
public record ProcessStartRequest(
        String processDefinitionId,
        String entityCode,
        String entityRecordId,
        String code,
        String submitterId,
        String submitterName,
        String processingStatus,
        Map<String, Object> data,
        Map<String, Object> variables) {

    /**
     * 初始化流程启动请求，保存构造参数供后续方法使用。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param code 实体记录的业务编号，后续写入流程变量以关联原始记录
     * @param submitterId 流程提交人 ID，后续用于发起记录和权限判断
     * @param submitterName 流程提交人名称，后续用于发起记录和页面展示
     * @param processingStatus 处理状态标识，决定后续流程启动采用的处理分支
     * @param data 数据，后续用于初始化流程启动并传递处理结果
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     */
    public ProcessStartRequest {
        Objects.requireNonNull(processDefinitionId, "processDefinitionId");
        Objects.requireNonNull(entityCode, "entityCode");
        Objects.requireNonNull(entityRecordId, "entityRecordId");
        data = immutableCopy(data);
        variables = immutableCopy(variables);
    }

    /**
     * 创建输入数据的不可变副本，避免调用方后续修改影响请求内容。
     *
     * @param source 原始键值数据；复制后与调用方后续修改隔离
     * @return 输入数据的不可变副本；输入为空时为空映射
     */
    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
