package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 复用实体运行时的权限与发布态查询，只向调用方返回独立业务投影。 */
@Component
@RequiredArgsConstructor
public class EntityRecordQueryAdapter implements EntityRecordQueryPort {
    private final EntityDataDynamicService records;

    /** 不绕过实体读取策略；不存在记录保持原有 null 语义。 */
    @Override
    public EntityRecordData findById(String entityCode, String recordId) {
        return EntityRecordProjection.project(records.findById(entityCode, recordId));
    }

    /** 条件白名单和数据范围过滤均由原实体服务执行。 */
    @Override
    public List<EntityRecordData> findByCondition(String entityCode, Map<String, Object> condition) {
        return records.findByCondition(entityCode, condition).stream().map(EntityRecordProjection::project).toList();
    }
}
