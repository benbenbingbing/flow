package com.workflow.service.entity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 实体表单解析服务
 * 
 * @description 根据流程状态动态解析应显示的实体表单
 *              - 新建数据时：解析第一个用户任务节点绑定的表单
 *              - 查看数据时：解析当前任务节点绑定的表单
 *              优化：使用冗余存储的 node_config 和 form_config 表，避免每次解析BPMN XML
 * @author Workflow Team
 * @version 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormResolveService {

    private final EntityFormMapper entityFormMapper;
    private final EntityFormFieldMapper formFieldMapper;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final com.workflow.mapper.EntityFieldMapper entityFieldMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final NodeConfigMapper nodeConfigMapper;
    private final FormConfigMapper formConfigMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    /**
     * 解析新建数据时应使用的表单
     * 
     * @description 查找流程的第一个用户任务节点，返回绑定的实体表单
     *              如果没有绑定表单，则返回实体的默认表单
     * @param entityCode 实体编码
     * @return 解析到的实体表单，如果没有则返回null
     */
    public EntityForm resolveFormForNewData(String entityCode) {
        // 1. 查找实体定义
        EntityDefinition entityDef = entityDefinitionMapper.findByEntityCode(entityCode)
            .orElse(null);
        
        if (entityDef == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }

        // 2. 检查是否绑定流程
        if (entityDef.getProcessDefinitionId() == null || !Boolean.TRUE.equals(entityDef.getEnableProcess())) {
            log.debug("实体[{}]未绑定流程，使用默认表单", entityCode);
            return getDefaultEntityForm(entityDef.getId());
        }

        // 3. 获取流程配置
        ProcessDefinitionConfig processConfig = processDefinitionConfigMapper.selectById(entityDef.getProcessDefinitionId());
        if (processConfig == null) {
            log.debug("实体[{}]绑定的流程不存在，使用默认表单", entityCode);
            return getDefaultEntityForm(entityDef.getId());
        }

        // 4. 从 node_config 表查找第一个用户任务节点（按创建时间排序）
        List<NodeConfig> userTaskNodes = nodeConfigMapper.findByProcessConfigId(processConfig.getId())
            .stream()
            .filter(node -> node.getNodeType() == NodeConfig.NodeType.USER_TASK)
            .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
            .collect(Collectors.toList());

        if (userTaskNodes.isEmpty()) {
            log.debug("流程[{}]未找到用户任务节点，使用默认表单", processConfig.getId());
            return getDefaultEntityForm(entityDef.getId());
        }

        // 5. 获取第一个用户任务节点绑定的表单
        NodeConfig firstNode = userTaskNodes.get(0);
        EntityForm nodeForm = getNodeBoundEntityForm(firstNode.getId());
        
        if (nodeForm != null) {
            log.debug("找到第一个节点[{}]绑定的表单: {}", firstNode.getNodeName(), nodeForm.getFormName());
            return nodeForm;
        }

        // 6. 没有绑定表单，返回默认表单
        log.debug("第一个节点[{}]未绑定表单，使用默认表单", firstNode.getNodeName());
        return getDefaultEntityForm(entityDef.getId());
    }

    /**
     * 解析查看数据时应使用的表单
     * 
     * @description 查找流程当前活动任务节点，返回绑定的实体表单
     *              如果没有当前任务或没有绑定表单，则返回实体的默认表单
     * @param entityCode   实体编码
     * @param entityDataId 实体数据ID
     * @return 解析到的实体表单，如果没有则返回null
     */
    public EntityForm resolveFormForViewData(String entityCode, Long entityDataId) {
        // 1. 查找实体定义
        EntityDefinition entityDef = entityDefinitionMapper.findByEntityCode(entityCode)
            .orElse(null);
        
        if (entityDef == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }

        // 2. 查找实体数据关联的流程实例
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(String.valueOf(entityDataId))
            .singleResult();

        if (processInstance == null) {
            log.debug("实体数据[{}]没有进行中的流程，使用默认表单", entityDataId);
            return getDefaultEntityForm(entityDef.getId());
        }

        // 3. 获取当前活动任务
        Task currentTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .active()
            .singleResult();

        if (currentTask == null) {
            log.debug("实体数据[{}]没有活动任务，使用默认表单", entityDataId);
            return getDefaultEntityForm(entityDef.getId());
        }

        // 4. 查找流程配置
        ProcessDefinitionConfig processConfig = processDefinitionConfigMapper.selectById(entityDef.getProcessDefinitionId());

        if (processConfig == null) {
            log.debug("未找到流程配置: {}", entityDef.getProcessDefinitionId());
            return getDefaultEntityForm(entityDef.getId());
        }

        // 5. 根据 taskDefinitionKey (node_id) 从 node_config 表查找节点
        NodeConfig currentNode = nodeConfigMapper.selectByNodeIdAndProcessId(
            currentTask.getTaskDefinitionKey(), 
            processConfig.getId()
        );

        if (currentNode == null) {
            log.debug("未找到节点配置: {}", currentTask.getTaskDefinitionKey());
            return getDefaultEntityForm(entityDef.getId());
        }

        // 6. 获取当前节点绑定的表单
        EntityForm nodeForm = getNodeBoundEntityForm(currentNode.getId());
        
        if (nodeForm != null) {
            log.debug("当前节点[{}]绑定表单: {}", currentNode.getNodeName(), nodeForm.getFormName());
            return nodeForm;
        }

        log.debug("当前节点[{}]未绑定表单，使用默认表单", currentNode.getNodeName());
        return getDefaultEntityForm(entityDef.getId());
    }

    /**
     * 获取节点绑定的实体表单
     * 
     * @description 根据节点配置ID查找绑定的表单配置，再转换为实体表单
     * @param nodeConfigId 节点配置ID（node_config 表的主键）
     * @return 绑定的实体表单，如果没有则返回null
     */
    private EntityForm getNodeBoundEntityForm(String nodeConfigId) {
        // 查询 form_config 表获取表单配置
        List<FormConfig> formConfigs = formConfigMapper.findByNodeConfigId(nodeConfigId);
        
        if (formConfigs == null || formConfigs.isEmpty()) {
            return null;
        }

        // 取第一个表单配置
        FormConfig formConfig = formConfigs.get(0);
        String formKey = formConfig.getFormKey();

        if (formKey == null || formKey.isEmpty()) {
            return null;
        }

        // form_key 格式: entityForm:{entityCode}:{formKey}
        if (formKey.startsWith("entityForm:")) {
            String[] parts = formKey.split(":");
            if (parts.length >= 3) {
                String entityCode = parts[1];
                String entityFormKey = parts[2];
                
                // 通过 entityCode 获取 entityId
                EntityDefinition entityDef = entityDefinitionMapper.findByEntityCode(entityCode)
                    .orElse(null);
                
                if (entityDef == null) {
                    return null;
                }
                
                LambdaQueryWrapper<EntityForm> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(EntityForm::getEntityId, entityDef.getId())
                       .eq(EntityForm::getFormKey, entityFormKey);
                
                EntityForm form = entityFormMapper.selectOne(wrapper);
                if (form != null) {
                    // 加载表单字段
                    loadFormFields(form);
                }
                return form;
            }
        }

        return null;
    }

    /**
     * 获取实体的默认表单
     * 
     * @description 查询实体下标记为 is_default=1 的表单
     * @param entityId 实体ID
     * @return 默认实体表单，如果没有则返回null
     */
    private EntityForm getDefaultEntityForm(String entityId) {
        LambdaQueryWrapper<EntityForm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EntityForm::getEntityId, entityId)
               .eq(EntityForm::getIsDefault, true)
               .orderByDesc(EntityForm::getUpdateTime)
               .last("LIMIT 1");
        
        EntityForm form = entityFormMapper.selectOne(wrapper);
        
        if (form != null) {
            loadFormFields(form);
        }
        
        return form;
    }

    /**
     * 加载表单字段
     * 
     * @description 根据表单ID查询关联的字段列表并设置到表单对象，同时加载字段编码
     * @param form 实体表单对象
     */
    private void loadFormFields(EntityForm form) {
        if (form == null || form.getId() == null) {
            return;
        }
        
        LambdaQueryWrapper<EntityFormField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EntityFormField::getFormId, form.getId())
               .orderByAsc(EntityFormField::getSortOrder);
        
        List<EntityFormField> fields = formFieldMapper.selectList(wrapper);
        
        // 加载字段编码（fieldCode）和选项配置
        for (EntityFormField field : fields) {
            if (field.getFieldId() != null) {
                // fieldId 存储的是 entity_field 的主键 ID（如 "266", "267"）
                // 使用自定义 SQL 查询
                com.workflow.entity.EntityField entityField = entityFieldMapper.findByIdString(field.getFieldId());
                if (entityField != null) {
                    field.setFieldCode(entityField.getFieldCode());
                    // 从实体字段补充选项配置（用于下拉、单选、多选等）
                    if (entityField.getOptionsJson() != null && !entityField.getOptionsJson().isEmpty()) {
                        field.setOptionsJson(entityField.getOptionsJson());
                    }
                } else {
                    // 查询不到，直接使用 fieldId 作为 fieldCode
                    field.setFieldCode(field.getFieldId());
                }
            }
        }
        
        form.setFields(fields);
    }
}
