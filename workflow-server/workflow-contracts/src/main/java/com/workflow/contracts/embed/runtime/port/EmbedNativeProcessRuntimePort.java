package com.workflow.contracts.embed.runtime.port;

import java.util.Optional;

/** 把原生流程实例反解为其所属实体记录，用于 Embed 固定目标校验。 */
public interface EmbedNativeProcessRuntimePort {

    /**
     * 读取流程启动时的服务端 entityCode/entityDataId 变量。
     * 未找到或变量不完整时返回空。
     */
    Optional<RecordTarget> findRecordTarget(String processInstanceId);

    /** 流程实例启动时保存的实体记录固定坐标。 */
    record RecordTarget(String entityCode, String recordId) {
    }
}
