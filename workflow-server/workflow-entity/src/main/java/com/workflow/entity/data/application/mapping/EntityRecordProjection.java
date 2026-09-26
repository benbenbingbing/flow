package com.workflow.entity.data.application.mapping;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.entity.data.api.response.EntityDataDTO;
import java.util.LinkedHashMap;

/** 将宿主响应映射成独立业务投影，查询与扩展分发使用相同字段协议。 */
public final class EntityRecordProjection {
    private EntityRecordProjection() {}
    /** 显式投影避免 HTTP DTO 新增字段时无意扩大插件契约。 */
    public static EntityRecordData project(EntityDataDTO source) {
        if (source == null) return null;
        EntityRecordData result = new EntityRecordData();
        result.setId(source.getId());
        result.setEntityCode(source.getEntityCode());
        result.setName(source.getName());
        result.setCode(source.getCode());
        result.setStatus(source.getStatus());
        result.setProcessStatus(source.getProcessStatus());
        result.setProcessInstanceId(source.getProcessInstanceId());
        result.setData(source.getData() == null ? null : new LinkedHashMap<>(source.getData()));
        result.setSubmitterId(source.getSubmitterId());
        result.setSubmitterName(source.getSubmitterName());
        result.setDeptId(source.getDeptId());
        result.setCreateBy(source.getCreateBy());
        result.setExtData(source.getExtData() == null ? null : new LinkedHashMap<>(source.getExtData()));
        return result;
    }
}
