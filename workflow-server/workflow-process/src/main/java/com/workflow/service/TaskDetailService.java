package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.TaskDetailDTO;
import com.workflow.entity.EntityData;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.ProcessTask;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import com.workflow.service.entity.EntityFormRuntimeService;
import com.workflow.service.form.EntityFormFieldRuntimeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务详情服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDetailService {
    
    private final ProcessTaskMapper processTaskMapper;
    private final EntityDataMapper entityDataMapper;
    private final com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper;
    private final com.workflow.mapper.EntityFormMapper entityFormMapper;
    private final com.workflow.mapper.EntityFieldMapper entityFieldMapper;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final EntityFormRuntimeService entityFormRuntimeService;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取任务详情（包含表单和实体数据）
     */
    public TaskDetailDTO getTaskDetail(String taskId) {
        TaskDetailDTO dto = new TaskDetailDTO();
        
        // 1. 获取流程待办信息
        ProcessTask processTask = processTaskMapper.selectByTaskId(taskId);
        if (processTask == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }
        dto.setProcessTask(processTask);
        
        // 2. 获取流程实例信息
        String processInstanceId = processTask.getProcessInstanceId();
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        
        TaskDetailDTO.ProcessInstanceDTO instanceDTO = new TaskDetailDTO.ProcessInstanceDTO();
        instanceDTO.setProcessInstanceId(processInstanceId);
        
        if (processInstance != null) {
            instanceDTO.setProcessName(processInstance.getName());
            instanceDTO.setStartUserId(processInstance.getStartUserId());
            instanceDTO.setBusinessKey(processInstance.getBusinessKey());
            instanceDTO.setStartTime(processInstance.getStartTime().toString());
        } else {
            // 流程可能已结束，从历史记录获取
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance != null) {
                instanceDTO.setProcessName(historicInstance.getName());
                instanceDTO.setStartUserId(historicInstance.getStartUserId());
                instanceDTO.setBusinessKey(historicInstance.getBusinessKey());
                if (historicInstance.getStartTime() != null) {
                    instanceDTO.setStartTime(historicInstance.getStartTime().toString());
                }
            }
        }
        dto.setProcessInstance(instanceDTO);
        
        // 3. 获取节点配置（表单绑定信息）
        String nodeId = processTask.getNodeId();
        String entityCode = processTask.getEntityCode();
        
        // 预加载实体字段映射（id -> fieldCode），用于表单字段转换
        Map<String, String> entityFieldCodeMap = new HashMap<>();
        if (entityCode != null) {
            try {
                com.workflow.entity.EntityDefinition entityDef = 
                    entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
                if (entityDef != null) {
                    List<com.workflow.entity.EntityField> entityFields = 
                        entityFieldMapper.findByEntityId(entityDef.getId());
                    for (com.workflow.entity.EntityField ef : entityFields) {
                        if (ef.getId() != null && ef.getFieldCode() != null) {
                            entityFieldCodeMap.put(ef.getId(), ef.getFieldCode());
                        }
                    }
                    log.info("实体字段映射: entityCode={}, fieldCount={}, map={}", entityCode, entityFieldCodeMap.size(), entityFieldCodeMap);
                }
            } catch (Exception e) {
                log.warn("获取实体字段映射失败: {}", e.getMessage());
            }
        }
        
        List<TaskDetailDTO.FormConfigDTO> formConfigs = new ArrayList<>();
        String formKey = processTask.getFormKey();

        if (processTask.getProcessDefinitionId() != null
                && nodeId != null) {
            try {
                List<com.workflow.entity.ProcessNodeForm> nodeForms =
                        processPublishedSnapshotService
                                .getNodeFormsByProcessDefinitionId(
                                        processTask.getProcessDefinitionId(),
                                        nodeId);
                for (com.workflow.entity.ProcessNodeForm nodeForm : nodeForms) {
                    EntityForm form = entityFormRuntimeService.getByBinding(nodeForm);
                    if (form != null) {
                        formConfigs.add(buildFormConfig(
                                form,
                                nodeForm,
                                entityFieldCodeMap,
                                formKey));
                    }
                }
            } catch (Exception exception) {
                log.warn(
                        "读取流程节点表单发布快照失败: processDefinitionId={}, nodeId={}, error={}",
                        processTask.getProcessDefinitionId(),
                        nodeId,
                        exception.getMessage());
            }
        }

        if (formConfigs.isEmpty() && entityCode != null) {
            try {
                com.workflow.entity.EntityDefinition entityDef =
                        entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
                if (entityDef != null) {
                    EntityForm form =
                            entityFormRuntimeService.getDefaultForm(entityDef.getId());
                    if (form == null) {
                        List<EntityForm> forms =
                                entityFormMapper.selectByEntityId(entityDef.getId());
                        EntityForm first = forms == null
                                ? null
                                : forms.stream()
                                        .filter(item -> item.getDeleted() == null
                                                || item.getDeleted() == 0)
                                        .findFirst()
                                        .orElse(null);
                        form = first == null
                                ? null
                                : entityFormRuntimeService.getById(first.getId());
                    }
                    if (form != null) {
                        formConfigs.add(buildFormConfig(
                                form,
                                null,
                                entityFieldCodeMap,
                                formKey));
                    }
                }
            } catch (Exception exception) {
                log.warn(
                        "获取实体默认发布表单失败: entityCode={}, error={}",
                        entityCode,
                        exception.getMessage());
            }
        }

        if (formConfigs.isEmpty()
                && formKey != null
                && entityCode != null) {
            try {
                com.workflow.entity.EntityDefinition entityDef =
                        entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
                EntityForm identity = entityDef == null
                        ? null
                        : entityFormMapper.selectByEntityIdAndFormKey(
                                entityDef.getId(),
                                formKey);
                EntityForm form = identity == null
                        ? null
                        : entityFormRuntimeService.getById(identity.getId());
                if (form != null) {
                    formConfigs.add(buildFormConfig(
                            form,
                            null,
                            entityFieldCodeMap,
                            formKey));
                }
            } catch (Exception exception) {
                log.warn(
                        "按 formKey 获取发布表单失败: formKey={}, error={}",
                        formKey,
                        exception.getMessage());
            }
        }

        if (formConfigs.isEmpty()) {
            TaskDetailDTO.FormConfigDTO empty = new TaskDetailDTO.FormConfigDTO();
            empty.setFormKey(formKey);
            empty.setFormName(processTask.getNodeName() + "表单");
            empty.setLayoutType("vertical");
            empty.setIsReadonly(true);
            formConfigs.add(empty);
        }
        dto.setFormConfigs(formConfigs);
        dto.setFormConfig(formConfigs.get(0));
        
        // 4. 获取实体数据
        String entityDataId = processTask.getEntityDataId();
        log.debug("获取实体数据: entityDataId={}, processInstanceId={}, entityCode={}", 
                entityDataId, processInstanceId, entityCode);
        
        // 优先根据流程实例ID查询实体数据
        try {
            EntityData entityData = entityDataMapper.findByProcessInstanceId(processInstanceId)
                    .orElse(null);
            if (entityData != null && entityData.getDataJson() != null) {
                Map<String, Object> dataMap = objectMapper.readValue(entityData.getDataJson(), Map.class);
                dto.setEntityData(dataMap);
                log.info("成功获取实体数据: processInstanceId={}, dataKeys={}", 
                        processInstanceId, dataMap.keySet());
            } else {
                log.warn("实体数据不存在或为空: processInstanceId={}", processInstanceId);
            }
        } catch (Exception e) {
            log.warn("获取实体数据失败: processInstanceId={}, error={}", processInstanceId, e.getMessage());
        }
        
        // 如果实体数据为空，尝试从流程变量获取
        if (dto.getEntityData() == null || dto.getEntityData().isEmpty()) {
            log.debug("尝试从流程变量获取数据: processInstanceId={}", processInstanceId);
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            // 过滤掉系统变量
            variables.remove("skipNodeEnabled");
            variables.remove("entityDataId");
            variables.remove("entityCode");
            variables.remove("submitterId");
            if (!variables.isEmpty()) {
                dto.setEntityData(variables);
                log.info("从流程变量获取数据: processInstanceId={}, dataKeys={}", 
                        processInstanceId, variables.keySet());
            }
        }
        
        // 5. 获取字段名称映射（用于前端显示字段名称而不是编码）
        if (entityCode != null && dto.getEntityData() != null) {
            try {
                Map<String, String> fieldNameMap = new HashMap<>();
                // 查询实体字段定义
                // 先根据 entityCode 查询 entityId
                com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
                if (entityDef != null) {
                    List<com.workflow.entity.EntityField> fields = entityFieldMapper.findByEntityId(entityDef.getId());
                    for (com.workflow.entity.EntityField field : fields) {
                        fieldNameMap.put(field.getFieldCode(), field.getFieldName());
                    }
                }
                dto.setFieldNameMap(fieldNameMap);
                log.debug("字段名称映射: entityCode={}, map={}", entityCode, fieldNameMap);
            } catch (Exception e) {
                log.warn("获取字段名称映射失败: {}", e.getMessage());
            }
        }
        
        // 最终日志：检查entityData和formConfig的匹配情况
        if (dto.getFormConfig() != null && dto.getFormConfig().getFields() != null && dto.getEntityData() != null) {
            List<String> fieldCodes = dto.getFormConfig().getFields().stream()
                    .map(f -> (String)f.get("fieldCode"))
                    .collect(Collectors.toList());
            Set<String> dataKeys = dto.getEntityData().keySet();
            log.info("任务详情数据匹配检查: taskId={}, fieldCodes={}, dataKeys={}, matched={}", 
                    taskId, fieldCodes, dataKeys, 
                    fieldCodes.stream().filter(dataKeys::contains).collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private TaskDetailDTO.FormConfigDTO buildFormConfig(
            EntityForm form,
            com.workflow.entity.ProcessNodeForm nodeForm,
            Map<String, String> entityFieldCodeMap,
            String fallbackFormKey) {
        TaskDetailDTO.FormConfigDTO formConfig =
                new TaskDetailDTO.FormConfigDTO();
        formConfig.setEntityFormId(form.getId());
        formConfig.setFormKey(form.getFormKey() == null
                ? fallbackFormKey
                : form.getFormKey());
        formConfig.setFormName(form.getFormName());
        formConfig.setLayoutType(form.getLayoutType());
        formConfig.setIsReadonly(nodeForm == null
                || Integer.valueOf(1).equals(nodeForm.getIsReadonly()));
        if (nodeForm != null) {
            formConfig.setFormReleaseId(nodeForm.getFormReleaseId());
            formConfig.setFormReleaseVersion(nodeForm.getFormReleaseVersion());
        }
        if (form.getFields() != null) {
            formConfig.setFields(form.getFields().stream()
                    .map(field -> convertFieldToMap(
                            field,
                            entityFieldCodeMap))
                    .collect(Collectors.toList()));
        }
        if (form.getNodes() != null) {
            formConfig.setNodes(objectMapper.convertValue(
                    form.getNodes(),
                    new com.fasterxml.jackson.core.type.TypeReference<
                            List<Map<String, Object>>>() {}));
        }
        return formConfig;
    }
    
    /**
     * 转换字段配置为Map
     * @param f 表单字段配置
     * @param entityFieldCodeMap 实体字段ID到fieldCode的映射
     */
    private Map<String, Object> convertFieldToMap(EntityFormField f, Map<String, String> entityFieldCodeMap) {
        // entity_form_field.field_id 存储的是 entity_field.id
        // 需要通过映射获取 fieldCode（如 "name", "gender"）
        String fieldCode = null;
        String fieldName = f.getFieldName();
        String fieldId = f.getFieldId();
        
        log.info("转换字段: fieldId={}, fieldName={}", fieldId, fieldName);
        
        // 首先从预加载的映射中获取 fieldCode
        if (fieldId != null && !fieldId.isEmpty() && entityFieldCodeMap != null) {
            fieldCode = entityFieldCodeMap.get(fieldId);
            if (fieldCode != null) {
                log.info("从映射获取fieldCode: fieldId={}, fieldCode={}", fieldId, fieldCode);
            } else {
                log.warn("映射中未找到fieldId: fieldId={}, availableKeys={}", fieldId, entityFieldCodeMap.keySet());
            }
        }
        
        // 查询 EntityField（获取 fieldCode 及引用实体配置）
        com.workflow.entity.EntityField entityField = null;
        if (fieldId != null && !fieldId.isEmpty()) {
            try {
                entityField = entityFieldMapper.selectById(fieldId);
                if (entityField != null) {
                    if (fieldCode == null) {
                        fieldCode = entityField.getFieldCode();
                    }
                    if (entityField.getFieldName() != null) {
                        fieldName = entityField.getFieldName();
                    }
                    log.debug("从EntityField获取: fieldId={}, fieldCode={}, fieldName={}",
                            fieldId, fieldCode, fieldName);
                } else {
                    log.warn("EntityField不存在: fieldId={}", fieldId);
                }
            } catch (Exception e) {
                log.warn("查询EntityField失败: fieldId={}, error={}", fieldId, e.getMessage());
            }
        }
        
        // 如果都无法获取，尝试直接使用 fieldId 作为 fieldCode（兼容情况）
        if (fieldCode == null && fieldId != null && !fieldId.isEmpty()) {
            fieldCode = fieldId;
            log.warn("使用fieldId作为fieldCode: {}", fieldCode);
        }
        
        // 最后的fallback
        if (fieldCode == null) {
            fieldCode = fieldName != null ? fieldName : "field_" + f.getId();
            log.warn("fieldCode为空，使用fallback: {}", fieldCode);
        }

        f.setFieldCode(fieldCode);
        f.setFieldName(fieldName);
        if (entityField != null) {
            if (entityField.getRefEntityId() != null) {
                f.setRefEntityId(entityField.getRefEntityId());
            }
            if (entityField.getRefEntityType() != null) {
                f.setRefEntityType(entityField.getRefEntityType().name());
            }
            if (entityField.getDisplayMode() != null) {
                f.setDisplayMode(entityField.getDisplayMode());
            }
            if (entityField.getRefFieldCode() != null) {
                f.setRefFieldCode(entityField.getRefFieldCode());
            }
        }

        return EntityFormFieldRuntimeMapper.toMap(f, null, objectMapper);
    }
    
}
