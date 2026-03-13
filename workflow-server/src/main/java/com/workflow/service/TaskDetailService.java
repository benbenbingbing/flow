package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.TaskDetailDTO;
import com.workflow.entity.EntityData;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessTask;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务详情服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDetailService {
    
    private final ProcessTaskMapper processTaskMapper;
    private final NodeConfigMapper nodeConfigMapper;
    private final EntityDataMapper entityDataMapper;
    private final com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper;
    private final com.workflow.mapper.EntityFormMapper entityFormMapper;
    private final com.workflow.mapper.EntityFormFieldMapper entityFormFieldMapper;
    private final com.workflow.mapper.EntityFieldMapper entityFieldMapper;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
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
        
        // 从数据库查询节点配置
        NodeConfig nodeConfig = null;
        if (entityCode != null) {
            com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper
                    .findByEntityCode(entityCode).orElse(null);
            if (entityDef != null && entityDef.getProcessDefinitionId() != null) {
                nodeConfig = nodeConfigMapper.selectByNodeIdAndProcessId(nodeId, 
                        entityDef.getProcessDefinitionId());
            }
        }
        
        TaskDetailDTO.FormConfigDTO formConfig = new TaskDetailDTO.FormConfigDTO();
        String formKey = null;
        
        if (nodeConfig != null && nodeConfig.getConfigJson() != null) {
            try {
                Map<String, Object> config = objectMapper.readValue(nodeConfig.getConfigJson(), Map.class);
                formKey = (String) config.get("formKey");
                formConfig.setFormKey(formKey);
                formConfig.setEntityFormId((String) config.get("entityFormId"));
                formConfig.setIsReadonly((Boolean) config.getOrDefault("isReadonly", true)); // 审批时默认只读
            } catch (Exception e) {
                log.warn("解析节点配置失败: {}", e.getMessage());
            }
        }
        
        // 设置表单默认值
        if (formKey == null) {
            formKey = processTask.getFormKey();
            formConfig.setFormKey(formKey);
        }
        formConfig.setFormName(processTask.getNodeName() + "表单");
        formConfig.setLayoutType("vertical"); // 默认布局
        formConfig.setIsReadonly(true); // 审批时表单只读
        
        // 查询表单字段配置
        if (formKey != null && entityCode != null) {
            try {
                com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper
                        .findByEntityCode(entityCode).orElse(null);
                if (entityDef != null) {
                    // 使用 EntityFormService 获取表单配置
                    EntityForm form = getFormByEntityIdAndFormKey(entityDef.getId(), formKey);
                    if (form != null) {
                        formConfig.setEntityFormId(form.getId());
                        formConfig.setFormName(form.getFormName());
                        formConfig.setLayoutType(form.getLayoutType());
                        
                        // 转换字段配置
                        if (form.getFields() != null && !form.getFields().isEmpty()) {
                            List<Map<String, Object>> fields = form.getFields().stream()
                                    .map(this::convertFieldToMap)
                                    .collect(Collectors.toList());
                            formConfig.setFields(fields);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取表单字段配置失败: formKey={}, error={}", formKey, e.getMessage());
            }
        }
        
        dto.setFormConfig(formConfig);
        
        // 4. 获取实体数据
        String entityDataId = processTask.getEntityDataId();
        String entityCode = processTask.getEntityCode();
        
        // 优先根据流程实例ID查询实体数据
        try {
            EntityData entityData = entityDataMapper.findByProcessInstanceId(processInstanceId)
                    .orElse(null);
            if (entityData != null && entityData.getDataJson() != null) {
                Map<String, Object> dataMap = objectMapper.readValue(entityData.getDataJson(), Map.class);
                dto.setEntityData(dataMap);
            }
        } catch (Exception e) {
            log.warn("获取实体数据失败: {}", e.getMessage());
        }
        
        // 如果实体数据为空，尝试从流程变量获取
        if (dto.getEntityData() == null) {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            // 过滤掉系统变量
            variables.remove("skipNodeEnabled");
            dto.setEntityData(variables);
        }
        
        // 5. 获取字段名称映射（用于前端显示字段名称而不是编码）
        if (entityCode != null && dto.getEntityData() != null) {
            try {
                Map<String, String> fieldNameMap = new HashMap<>();
                // 查询实体字段定义
                List<com.workflow.entity.EntityField> fields = entityFieldMapper.findByEntityCode(entityCode);
                for (com.workflow.entity.EntityField field : fields) {
                    fieldNameMap.put(field.getFieldId(), field.getFieldName());
                }
                dto.setFieldNameMap(fieldNameMap);
                log.debug("字段名称映射: entityCode={}, map={}", entityCode, fieldNameMap);
            } catch (Exception e) {
                log.warn("获取字段名称映射失败: {}", e.getMessage());
            }
        }
        
        return dto;
    }
    
    /**
     * 根据实体ID和表单Key查询表单
     */
    private EntityForm getFormByEntityIdAndFormKey(String entityId, String formKey) {
        EntityForm form = entityFormMapper.selectByEntityIdAndFormKey(entityId, formKey);
        if (form != null) {
            // 填充字段
            List<com.workflow.entity.EntityFormField> fields = entityFormFieldMapper.selectByFormId(form.getId());
            form.setFields(fields);
        }
        return form;
    }
    
    /**
     * 转换字段配置为Map
     */
    private Map<String, Object> convertFieldToMap(EntityFormField f) {
        Map<String, Object> field = new HashMap<>();
        field.put("id", f.getId());
        field.put("fieldCode", f.getFieldId()); // 实体字段编码
        field.put("fieldName", f.getFieldName());
        field.put("fieldLabel", f.getFieldLabel());
        field.put("fieldType", f.getFieldType());
        field.put("componentType", f.getComponentType());
        field.put("isRequired", f.getIsRequired());
        field.put("isReadonly", f.getIsReadonly());
        field.put("isHidden", f.getIsHidden());
        field.put("defaultValue", f.getDefaultValue());
        field.put("placeholder", f.getPlaceholder());
        field.put("sortOrder", f.getSortOrder());
        field.put("gridSpan", f.getGridSpan());
        // 解析组件属性JSON
        if (f.getComponentProps() != null) {
            try {
                field.put("componentProps", objectMapper.readValue(f.getComponentProps(), Map.class));
            } catch (Exception e) {
                field.put("componentProps", new HashMap<>());
            }
        }
        return field;
    }
}
