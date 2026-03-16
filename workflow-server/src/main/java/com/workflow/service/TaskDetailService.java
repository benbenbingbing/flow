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
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
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
        
        TaskDetailDTO.FormConfigDTO formConfig = new TaskDetailDTO.FormConfigDTO();
        String entityFormId = null;
        
        // 从 BPMN XML 中解析节点的表单配置
        try {
            String processKey = processTask.getProcessKey();
            log.debug("解析 BPMN 表单配置: processKey={}, nodeId={}", processKey, nodeId);
            if (processKey != null) {
                com.workflow.entity.ProcessDefinitionConfig processConfig = 
                    processDefinitionConfigMapper.findByProcessKey(processKey).orElse(null);
                if (processConfig != null && processConfig.getBpmnXml() != null) {
                    entityFormId = parseEntityFormIdFromBpmn(processConfig.getBpmnXml(), nodeId);
                    if (entityFormId != null) {
                        formConfig.setEntityFormId(entityFormId);
                        log.debug("从 BPMN 解析到 entityFormId: {}", entityFormId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 BPMN 解析表单配置失败: {}", e.getMessage());
        }
        
        // 设置表单默认值
        String formKey = processTask.getFormKey();
        formConfig.setFormKey(formKey);
        formConfig.setFormName(processTask.getNodeName() + "表单");
        formConfig.setLayoutType("vertical"); // 默认布局
        if (formConfig.getIsReadonly() == null) {
            formConfig.setIsReadonly(true); // 审批时表单默认只读
        }
        
        // 查询表单字段配置 - 优先使用 entityFormId
        if (entityFormId != null) {
            try {
                EntityForm form = getFormById(entityFormId);
                if (form != null) {
                    formConfig.setEntityFormId(form.getId());
                    formConfig.setFormName(form.getFormName());
                    formConfig.setLayoutType(form.getLayoutType());
                    
                    // 转换字段配置
                    if (form.getFields() != null && !form.getFields().isEmpty()) {
                        final Map<String, String> fieldCodeMap = entityFieldCodeMap;
                        List<Map<String, Object>> fields = form.getFields().stream()
                                .map(f -> convertFieldToMap(f, fieldCodeMap))
                                .collect(Collectors.toList());
                        formConfig.setFields(fields);
                        log.info("表单字段配置: entityFormId={}, fieldCodes={}", 
                                entityFormId, fields.stream().map(f -> (String)f.get("fieldCode")).collect(Collectors.toList()));
                    } else {
                        log.warn("表单字段为空: entityFormId={}", entityFormId);
                    }
                }
            } catch (Exception e) {
                log.warn("获取表单字段配置失败: entityFormId={}, error={}", entityFormId, e.getMessage());
            }
        } else if (formKey != null && entityCode != null) {
            // 备用：使用 formKey 查询
            try {
                com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper
                        .findByEntityCode(entityCode).orElse(null);
                if (entityDef != null) {
                    EntityForm form = getFormByEntityIdAndFormKey(entityDef.getId(), formKey);
                    if (form != null) {
                        formConfig.setEntityFormId(form.getId());
                        formConfig.setFormName(form.getFormName());
                        formConfig.setLayoutType(form.getLayoutType());
                        
                        // 转换字段配置
                        if (form.getFields() != null && !form.getFields().isEmpty()) {
                            final Map<String, String> fieldCodeMap = entityFieldCodeMap;
                            List<Map<String, Object>> fields = form.getFields().stream()
                                    .map(f -> convertFieldToMap(f, fieldCodeMap))
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
    
    /**
     * 根据表单ID查询表单
     */
    private EntityForm getFormById(String formId) {
        EntityForm form = entityFormMapper.selectById(formId);
        if (form != null) {
            // 填充字段
            List<com.workflow.entity.EntityFormField> fields = entityFormFieldMapper.selectByFormId(form.getId());
            form.setFields(fields);
        }
        return form;
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
     * @param f 表单字段配置
     * @param entityFieldCodeMap 实体字段ID到fieldCode的映射
     */
    private Map<String, Object> convertFieldToMap(EntityFormField f, Map<String, String> entityFieldCodeMap) {
        Map<String, Object> field = new HashMap<>();
        field.put("id", f.getId());
        
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
        
        // 如果映射中没有，尝试直接查询 EntityField
        if (fieldCode == null && fieldId != null && !fieldId.isEmpty()) {
            try {
                com.workflow.entity.EntityField entityField = entityFieldMapper.selectById(fieldId);
                if (entityField != null) {
                    fieldCode = entityField.getFieldCode();
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
        
        field.put("fieldCode", fieldCode);
        field.put("fieldName", fieldName);
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
    
    /**
     * 从 BPMN XML 中解析指定节点的 entityFormId
     */
    private String parseEntityFormIdFromBpmn(String bpmnXml, String nodeId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes("UTF-8")));
            
            // 查找指定 id 的 userTask 元素
            NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                Element userTask = (Element) userTasks.item(i);
                if (nodeId.equals(userTask.getAttribute("id"))) {
                    // 查找 extensionElements -> properties -> property
                    NodeList extElements = userTask.getElementsByTagNameNS("*", "extensionElements");
                    for (int j = 0; j < extElements.getLength(); j++) {
                        Element extElement = (Element) extElements.item(j);
                        NodeList properties = extElement.getElementsByTagNameNS("*", "properties");
                        for (int k = 0; k < properties.getLength(); k++) {
                            Element props = (Element) properties.item(k);
                            NodeList propList = props.getElementsByTagNameNS("*", "property");
                            for (int m = 0; m < propList.getLength(); m++) {
                                Element prop = (Element) propList.item(m);
                                String name = prop.getAttribute("name");
                                String value = prop.getAttribute("value");
                                if ("entityFormId".equals(name) && value != null && !value.isEmpty()) {
                                    return value;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 BPMN XML 失败: {}", e.getMessage());
        }
        return null;
    }
}
