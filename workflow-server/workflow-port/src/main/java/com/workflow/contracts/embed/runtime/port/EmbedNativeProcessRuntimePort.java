package com.workflow.contracts.embed.runtime.port;

import java.util.Optional;

/** 把原生流程实例反解为其所属实体记录，用于 Embed 固定目标校验。 */
public interface EmbedNativeProcessRuntimePort {

    /**
     * 读取流程启动时的服务端 entityCode/entityDataId 变量。
     * 未找到或变量不完整时返回空。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 匹配的记录目标；未找到时为空
     */
    Optional<RecordTarget> findRecordTarget(String processInstanceId);

    /**
     * 流程实例启动时保存的实体记录固定坐标。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    record RecordTarget(String entityCode, String recordId) {
    }
}
