package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.model.EntityRecordPage;
import com.workflow.contracts.entity.port.SystemEntityQueryPort;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/** 延用系统实体的专用投影和字段白名单，保持与系统列表的读取范围一致。 */
@Component
@RequiredArgsConstructor
public class SystemEntityQueryAdapter implements SystemEntityQueryPort {
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
        var result = records.findPage(entityCode, filters, pageNumber, pageSize);
        return new EntityRecordPage(result.getRecords().stream().map(EntityRecordProjection::project).toList(),
                result.getTotal(), result.getPageNum(), result.getPageSize());
    }

    private void requireActor() {
        var actor = actors.current();
        if (actor == null || !StringUtils.hasText(actor.userId())) throw new ForbiddenException("读取系统实体需要登录身份");
    }
}
