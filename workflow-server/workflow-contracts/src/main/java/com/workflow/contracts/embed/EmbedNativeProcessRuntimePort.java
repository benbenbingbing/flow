package com.workflow.contracts.embed;

import java.util.Optional;

/** 把原生流程实例反解为其所属实体记录，用于 Embed 固定目标校验。 */
public interface EmbedNativeProcessRuntimePort {

    /**
     * 读取流程启动时的服务端 entityCode/entityDataId 变量。
     * 未找到或变量不完整时必须返回 empty。
     */
    Optional<RecordTarget> findRecordTarget(String processInstanceId);

    record RecordTarget(String entityCode, String recordId) {
    }
}
