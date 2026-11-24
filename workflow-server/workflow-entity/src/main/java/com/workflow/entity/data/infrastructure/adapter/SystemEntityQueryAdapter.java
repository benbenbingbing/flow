package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.model.EntityRecordPage;
import com.workflow.port.system.SystemEntityRecordPort;
import com.workflow.port.system.SystemEntityDefinitionPort;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 延用系统实体的专用投影和字段白名单，由调用方控制实体和记录的访问范围。 */
@Component
@RequiredArgsConstructor
public class SystemEntityQueryAdapter implements SystemEntityRecordPort, SystemEntityDefinitionPort {
    private final SystemEntityReadService records;

    @Override
    public EntityRecordData requireById(String entityCode, String recordId) {
        return EntityRecordProjection.project(records.findByIdForPort(entityCode, recordId));
    }

    @Override
    public EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize) {
        return project(records.pageForPort(entityCode, filters, pageNumber, pageSize, null, null, null));
    }

    @Override
    public EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize,
            String sortField, boolean descending, Set<String> fields) {
        return project(records.pageForPort(entityCode, filters, pageNumber, pageSize, sortField,
                descending ? "DESC" : "ASC", fields));
    }

    @Override
    public List<EntityRecordData> findByIds(String entityCode, List<String> recordIds) {
        return records.findByIdsForPort(entityCode, recordIds).stream().map(EntityRecordProjection::project).toList();
    }

    @Override
    public List<Summary> listReadable() {
        return records.listRegisteredForPort();
    }

    @Override
    public List<Field> describeReadableFields(String entityCode) {
        return records.describeReadableFieldsForPort(entityCode);
    }

    private EntityRecordPage project(PageResult<EntityDataDTO> result) {
        return new EntityRecordPage(result.getRecords().stream().map(EntityRecordProjection::project).toList(),
                result.getTotal(), result.getPageNum(), result.getPageSize());
    }

}
