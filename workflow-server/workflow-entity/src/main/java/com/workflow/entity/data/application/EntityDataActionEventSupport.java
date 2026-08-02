package com.workflow.entity.data.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves configured UI event origins and builds their runtime requests. */
@Component
public class EntityDataActionEventSupport {

    private final EntityListActionConfigService actionConfigService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFormMapper formMapper;

    EntityDataActionEventSupport(
            EntityListActionConfigService actionConfigService,
            EntityDefinitionMapper definitionMapper,
            EntityFormMapper formMapper) {
        this.actionConfigService = actionConfigService;
        this.definitionMapper = definitionMapper;
        this.formMapper = formMapper;
    }

    EventOrigin resolve(String entityCode, String listKey, String formId) {
        if (formId != null && !formId.isBlank()) {
            EntityForm form = formMapper.selectById(formId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在: " + formId);
            }
            EntityDefinition entity = definitionMapper.selectById(form.getEntityId());
            if (entity == null || !entityCode.equals(entity.getEntityCode())) {
                throw new IllegalArgumentException("表单与实体不匹配");
            }
            return new EventOrigin("FORM", form.getId());
        }
        EntityDefinition entity = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entity != null) {
            EntityForm form = formMapper.selectDefaultByEntityId(entity.getId());
            if (form != null) {
                return new EventOrigin("FORM", form.getId());
            }
        }
        return list(entityCode, listKey);
    }

    EventOrigin list(String entityCode, String listKey) {
        EntityListConfig list = actionConfigService.resolveListConfig(entityCode, listKey);
        return list == null ? null : new EventOrigin("LIST", list.getId());
    }

    UiEventExecuteRequest request(
            String eventCode,
            EventOrigin origin,
            String entityCode,
            String listKey,
            String recordId,
            Map<String, Object> input) {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setEventCode(eventCode);
        request.setConfigType(origin.configType());
        request.setConfigId(origin.configId());
        request.setEntityCode(entityCode);
        request.setListKey(listKey);
        request.setRecordId(recordId);
        request.setInput(input);
        request.setContext(Map.of(
                "formId", "FORM".equals(origin.configType()) ? origin.configId() : "",
                "listId", "LIST".equals(origin.configType()) ? origin.configId() : ""));
        return request;
    }

    record EventOrigin(String configType, String configId) {
    }
}
