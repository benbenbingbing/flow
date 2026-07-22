package com.workflow.service;

import com.workflow.common.ForbiddenException;
import com.workflow.common.UserContext;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.permission.EntityListActionConfigService;
import com.workflow.service.permission.EntityListScopeAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实体数据功能权限、数据范围与按钮规则统一执行入口。
 */
@Service
@RequiredArgsConstructor
public class EntityDataActionService {

    private final EntityDataDynamicService dynamicService;
    private final EntityListActionConfigService actionConfigService;
    private final EntityActionCapabilityService capabilityService;
    private final EntityListScopeAuditService scopeAuditService;
    private final PublishedFormSubmissionService formSubmissionService;
    private final FormSubmissionTraceService formSubmissionTraceService;

    @Transactional(readOnly = true)
    public EntityDataDTO getDetail(String entityCode, String id, String listKey) {
        EntityDataDTO row = findAccessible(entityCode, id, listKey);
        capabilityService.requireRowAction(entityCode, listKey, "view", row);
        return row;
    }

    @Transactional(readOnly = true)
    public EntityDataDTO getDetailByProcessInstance(
            String entityCode,
            String processInstanceId,
            String listKey) {
        EntityListConfig config = actionConfigService.resolveListConfig(entityCode, listKey);
        return dynamicService.findAccessibleByProcessInstanceId(
                entityCode,
                processInstanceId,
                config == null ? null : config.getListKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO create(EntityDataDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getEntityCode())) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        capabilityService.requireToolbarAction(dto.getEntityCode(), dto.getListKey(), "create");
        FormSubmissionExecutionContext executionContext =
                formSubmissionTraceService.current(
                        "ENTITY_CREATE",
                        null,
                        Map.of(
                                "entityCode",
                                dto.getEntityCode(),
                                "mode",
                                "create"));
        dto.setData(formSubmissionService.applyDefaultForm(
                dto.getEntityCode(),
                null,
                "create",
                dto.getData(),
                executionContext));
        return dynamicService.save(dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO update(
            String entityCode,
            String id,
            String listKey,
            Map<String, Object> formData) {
        EntityDataDTO row = findAccessible(entityCode, id, listKey);
        capabilityService.requireRowAction(entityCode, listKey, "edit", row);
        FormSubmissionExecutionContext executionContext =
                formSubmissionTraceService.current(
                        "ENTITY_UPDATE",
                        null,
                        Map.of(
                                "entityCode",
                                entityCode,
                                "recordId",
                                id,
                                "mode",
                                "edit"));
        Map<String, Object> safeData =
                formSubmissionService.applyDefaultForm(
                        entityCode,
                        id,
                        "edit",
                        formData,
                        executionContext);
        return dynamicService.update(
                entityCode,
                id,
                Map.of("data", safeData));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String entityCode, String id, String listKey) {
        EntityDataDTO row = findAccessible(entityCode, id, listKey);
        capabilityService.requireRowAction(entityCode, listKey, "delete", row);
        dynamicService.delete(entityCode, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(String entityCode, List<String> ids, String listKey) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的数据");
        }
        List<EntityDataDTO> rows = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        for (String id : ids.stream().filter(StringUtils::hasText).distinct().toList()) {
            EntityDataDTO row = findAccessible(entityCode, id, listKey);
            rows.add(row);
            var capability = capabilityService.evaluateRowAction(entityCode, listKey, "batchDelete", row);
            if (!capability.isVisible() || !capability.isEnabled()) {
                denied.add((StringUtils.hasText(row.getDataNo()) ? row.getDataNo() : row.getId())
                        + "：" + capability.getReason());
            }
        }
        if (!denied.isEmpty()) {
            throw new ForbiddenException("批量删除被阻止：" + String.join("；", denied));
        }
        for (EntityDataDTO row : rows) {
            dynamicService.delete(entityCode, row.getId());
        }
    }

    private EntityDataDTO findAccessible(String entityCode, String id, String listKey) {
        EntityListConfig config = actionConfigService.resolveListConfig(entityCode, listKey);
        try {
            return dynamicService.findAccessibleById(
                    entityCode,
                    id,
                    config == null ? null : config.getListKey());
        } catch (ForbiddenException exception) {
            scopeAuditService.record(
                    entityCode,
                    config == null ? listKey : config.getListKey(),
                    UserContext.getUserId(),
                    "DENY",
                    "DENIED",
                    java.util.Map.of(
                            "dataId", id,
                            "reason", exception.getMessage()));
            throw exception;
        }
    }
}
