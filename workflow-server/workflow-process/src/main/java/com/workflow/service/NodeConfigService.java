package com.workflow.service;

import com.workflow.dto.*;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程节点配置服务。
 *
 * <p>管理流程节点的基础配置、审批人配置与表单配置，提供节点配置的查询、
 * 新增（含级联保存审批人、表单及表单字段）与删除能力。</p>
 */
@Service
@RequiredArgsConstructor
public class NodeConfigService {
    
    /** 节点配置 Mapper */
    private final NodeConfigMapper nodeMapper;
    /** 流程定义配置 Mapper */
    private final ProcessDefinitionConfigMapper processMapper;
    /** 审批人配置 Mapper */
    private final AssigneeConfigMapper assigneeMapper;
    /** 表单配置 Mapper */
    private final FormConfigMapper formMapper;
    /** 表单字段配置 Mapper */
    private final FormFieldConfigMapper fieldMapper;
    
    /**
     * 查询指定流程下的所有节点配置（含审批人、表单及字段）。
     *
     * @param processId 流程配置ID
     * @return 节点配置列表
     */
    @Transactional(readOnly = true)
    public List<NodeConfigDTO> findByProcessId(String processId) {
        return nodeMapper.findByProcessConfigId(processId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据节点配置ID查询详情（含审批人、表单及字段）。
     *
     * @param id 节点配置ID
     * @return 节点配置详情
     * @throws RuntimeException 节点不存在时抛出
     */
    @Transactional(readOnly = true)
    public NodeConfigDTO findById(String id) {
        NodeConfig node = nodeMapper.selectById(id);
        if (node == null) {
            throw new RuntimeException("Node not found: " + id);
        }
        return convertToDTO(node);
    }
    
    /**
     * 保存节点配置，并级联保存审批人、表单及表单字段。
     *
     * @param processId 所属流程配置ID
     * @param dto       节点配置数据
     * @return 保存后的节点配置
     * @throws RuntimeException 所属流程不存在时抛出
     */
    @Transactional
    public NodeConfigDTO save(String processId, NodeConfigDTO dto) {
        ProcessDefinitionConfig process = processMapper.selectById(processId);
        if (process == null) {
            throw new RuntimeException("Process not found: " + processId);
        }
        
        NodeConfig node = convertToEntity(dto);
        node.setProcessConfigId(processId);
        nodeMapper.insert(node);
        
        // Save assignees
        if (dto.getAssignees() != null) {
            for (AssigneeConfigDTO assigneeDTO : dto.getAssignees()) {
                AssigneeConfig assignee = convertToEntity(assigneeDTO);
                assignee.setNodeConfigId(node.getId());
                assigneeMapper.insert(assignee);
            }
        }
        
        // Save forms
        if (dto.getForms() != null) {
            for (FormConfigDTO formDTO : dto.getForms()) {
                FormConfig form = convertToEntity(formDTO);
                form.setNodeConfigId(node.getId());
                formMapper.insert(form);
                
                // Save form fields
                if (formDTO.getFields() != null) {
                    for (FormFieldConfigDTO fieldDTO : formDTO.getFields()) {
                        FormFieldConfig field = convertToEntity(fieldDTO);
                        field.setFormConfigId(form.getId());
                        fieldMapper.insert(field);
                    }
                }
            }
        }
        
        return convertToDTO(node);
    }
    
    /**
     * 删除指定节点配置。
     *
     * @param id 节点配置ID
     */
    @Transactional
    public void delete(String id) {
        nodeMapper.deleteById(id);
    }
    
    /** 将节点配置实体转换为DTO，并加载关联的审批人、表单及字段 */
    private NodeConfigDTO convertToDTO(NodeConfig node) {
        NodeConfigDTO dto = new NodeConfigDTO();
        dto.setId(node.getId());
        dto.setNodeId(node.getNodeId());
        dto.setNodeName(node.getNodeName());
        dto.setNodeType(node.getNodeType());
        dto.setConfigJson(node.getConfigJson());
        dto.setSkipNode(node.getSkipNode());
        
        // Load assignees
        List<AssigneeConfig> assignees = assigneeMapper.findByNodeConfigId(node.getId());
        if (assignees != null && !assignees.isEmpty()) {
            dto.setAssignees(assignees.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList()));
        }
        
        // Load forms
        List<FormConfig> forms = formMapper.findByNodeConfigId(node.getId());
        if (forms != null && !forms.isEmpty()) {
            dto.setForms(forms.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private AssigneeConfigDTO convertToDTO(AssigneeConfig assignee) {
        AssigneeConfigDTO dto = new AssigneeConfigDTO();
        dto.setId(assignee.getId());
        dto.setAssigneeType(assignee.getAssigneeType());
        dto.setAssigneeValue(assignee.getAssigneeValue());
        dto.setAssigneeName(assignee.getAssigneeName());
        dto.setPriority(assignee.getPriority());
        return dto;
    }
    
    private FormConfigDTO convertToDTO(FormConfig form) {
        FormConfigDTO dto = new FormConfigDTO();
        dto.setId(form.getId());
        dto.setFormName(form.getFormName());
        dto.setFormKey(form.getFormKey());
        dto.setDescription(form.getDescription());
        dto.setIsReadonly(form.getIsReadonly());
        
        // Load form fields
        List<FormFieldConfig> fields = fieldMapper.findByFormConfigId(form.getId());
        if (fields != null && !fields.isEmpty()) {
            dto.setFields(fields.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private FormFieldConfigDTO convertToDTO(FormFieldConfig field) {
        FormFieldConfigDTO dto = new FormFieldConfigDTO();
        dto.setId(field.getId());
        dto.setFieldName(field.getFieldName());
        dto.setFieldKey(field.getFieldKey());
        dto.setFieldType(field.getFieldType());
        dto.setIsRequired(field.getIsRequired());
        dto.setDefaultValue(field.getDefaultValue());
        dto.setOptionsJson(field.getOptionsJson());
        dto.setValidateRules(field.getValidateRules());
        dto.setSortOrder(field.getSortOrder());
        return dto;
    }
    
    private NodeConfig convertToEntity(NodeConfigDTO dto) {
        NodeConfig node = new NodeConfig();
        node.setId(dto.getId());
        node.setNodeId(dto.getNodeId());
        node.setNodeName(dto.getNodeName());
        node.setNodeType(dto.getNodeType());
        node.setConfigJson(dto.getConfigJson());
        node.setSkipNode(dto.getSkipNode());
        return node;
    }
    
    private AssigneeConfig convertToEntity(AssigneeConfigDTO dto) {
        AssigneeConfig assignee = new AssigneeConfig();
        assignee.setId(dto.getId());
        assignee.setAssigneeType(dto.getAssigneeType());
        assignee.setAssigneeValue(dto.getAssigneeValue());
        assignee.setAssigneeName(dto.getAssigneeName());
        assignee.setPriority(dto.getPriority());
        return assignee;
    }
    
    private FormConfig convertToEntity(FormConfigDTO dto) {
        FormConfig form = new FormConfig();
        form.setId(dto.getId());
        form.setFormName(dto.getFormName());
        form.setFormKey(dto.getFormKey());
        form.setDescription(dto.getDescription());
        form.setIsReadonly(dto.getIsReadonly());
        return form;
    }
    
    private FormFieldConfig convertToEntity(FormFieldConfigDTO dto) {
        FormFieldConfig field = new FormFieldConfig();
        field.setId(dto.getId());
        field.setFieldName(dto.getFieldName());
        field.setFieldKey(dto.getFieldKey());
        field.setFieldType(dto.getFieldType());
        field.setIsRequired(dto.getIsRequired());
        field.setDefaultValue(dto.getDefaultValue());
        field.setOptionsJson(dto.getOptionsJson());
        field.setValidateRules(dto.getValidateRules());
        field.setSortOrder(dto.getSortOrder());
        return field;
    }
}
