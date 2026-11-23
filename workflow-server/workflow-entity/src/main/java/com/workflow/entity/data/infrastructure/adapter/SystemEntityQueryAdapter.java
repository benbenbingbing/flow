package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.model.EntityRecordPage;
import com.workflow.port.system.SystemEntityRecordPort;
import com.workflow.port.system.SystemEntityDefinitionPort;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.core.result.PageResult;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 延用系统实体的专用投影和字段白名单，保持与系统列表的读取范围一致。 */
@Component
@RequiredArgsConstructor
public class SystemEntityQueryAdapter implements SystemEntityRecordPort, SystemEntityDefinitionPort {
    private final SystemEntityReadService records;
    private final CurrentActorPort actors;

    @Override
    public EntityRecordData requireById(String entityCode, String recordId) {
        requireActor();
        return EntityRecordProjection.project(records.findById(entityCode, recordId));
    }

    @Override
    public EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize) {
        requireActor();
        return project(records.findPage(entityCode, filters, pageNumber, pageSize));
    }

    @Override
    public EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize,
            String sortField, boolean descending, Set<String> fields) {
        requireActor();
        return project(records.findPage(entityCode, filters, pageNumber, pageSize, sortField,
                descending ? "DESC" : "ASC", fields));
    }

    @Override
    public List<EntityRecordData> findByIds(String entityCode, List<String> recordIds) {
        requireActor();
        return records.findByIds(entityCode, recordIds).stream().map(EntityRecordProjection::project).toList();
    }

    @Override
    public List<Summary> listReadable() {
        requireActor();
        return records.listReadable();
    }

    @Override
    public List<Field> describeReadableFields(String entityCode) {
        requireActor();
        return records.describeReadableFields(entityCode);
    }

    private EntityRecordPage project(PageResult<EntityDataDTO> result) {
        return new EntityRecordPage(result.getRecords().stream().map(EntityRecordProjection::project).toList(),
                result.getTotal(), result.getPageNum(), result.getPageSize());
    }

    private void requireActor() {
        var actor = actors.current();
        if (actor == null || !StringUtils.hasText(actor.userId())) throw new ForbiddenException("读取系统实体需要登录身份");
    }
}
