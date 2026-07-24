package com.workflow.dto;

import com.workflow.entity.ProcessTask;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务详情DTO（包含表单和实体数据）
 */
public class TaskDetailDTO {
    
    /**
     * 流程待办信息
     */
    private ProcessTask processTask;
    
    /**
     * 实体数据
     */
    private Map<String, Object> entityData;
    
    /**
     * 字段名称映射（key: 字段编码, value: 字段名称）
     */
    private Map<String, String> fieldNameMap;
    
    /**
     * 表单配置
     */
    private FormConfigDTO formConfig;

    /**
     * 多表单配置列表。保留 formConfig 作为第一个表单的兼容字段。
     */
    private List<FormConfigDTO> formConfigs;
    
    /**
     * 流程实例信息
     */
    private ProcessInstanceDTO processInstance;
    
    // Getter 和 Setter 方法
    public ProcessTask getProcessTask() {
        return processTask;
    }
    
    public void setProcessTask(ProcessTask processTask) {
        this.processTask = processTask;
    }
    
    public Map<String, Object> getEntityData() {
        return entityData;
    }
    
    public void setEntityData(Map<String, Object> entityData) {
        this.entityData = entityData;
    }
    
    public Map<String, String> getFieldNameMap() {
        return fieldNameMap;
    }
    
    public void setFieldNameMap(Map<String, String> fieldNameMap) {
        this.fieldNameMap = fieldNameMap;
    }
    
    public FormConfigDTO getFormConfig() {
        return formConfig;
    }
    
    public void setFormConfig(FormConfigDTO formConfig) {
        this.formConfig = formConfig;
    }

    public List<FormConfigDTO> getFormConfigs() {
        return formConfigs;
    }

    public void setFormConfigs(List<FormConfigDTO> formConfigs) {
        this.formConfigs = formConfigs;
    }
    
    public ProcessInstanceDTO getProcessInstance() {
        return processInstance;
    }
    
    public void setProcessInstance(ProcessInstanceDTO processInstance) {
        this.processInstance = processInstance;
    }
    
    /**
     * 表单配置DTO（任务详情中展示的表单信息）
     */
    public static class FormConfigDTO {
        /** 表单Key */
        private String formKey;
        /** 实体表单ID */
        private String entityFormId;
        /** 表单发布版本ID */
        private String formReleaseId;
        /** 表单发布版本号 */
        private Integer formReleaseVersion;
        /** 表单名称 */
        private String formName;
        /** 布局类型 */
        private String layoutType;
        /** 是否只读 */
        private Boolean isReadonly;
        /** 表单字段列表 */
        private List<Map<String, Object>> fields;
        /** 表单节点列表（递归结构） */
        private List<Map<String, Object>> nodes;
        
        public String getFormKey() {
            return formKey;
        }
        
        public void setFormKey(String formKey) {
            this.formKey = formKey;
        }
        
        public String getEntityFormId() {
            return entityFormId;
        }
        
        public void setEntityFormId(String entityFormId) {
            this.entityFormId = entityFormId;
        }

        public String getFormReleaseId() {
            return formReleaseId;
        }

        public void setFormReleaseId(String formReleaseId) {
            this.formReleaseId = formReleaseId;
        }

        public Integer getFormReleaseVersion() {
            return formReleaseVersion;
        }

        public void setFormReleaseVersion(Integer formReleaseVersion) {
            this.formReleaseVersion = formReleaseVersion;
        }
        
        public String getFormName() {
            return formName;
        }
        
        public void setFormName(String formName) {
            this.formName = formName;
        }
        
        public String getLayoutType() {
            return layoutType;
        }
        
        public void setLayoutType(String layoutType) {
            this.layoutType = layoutType;
        }
        
        public Boolean getIsReadonly() {
            return isReadonly;
        }
        
        public void setIsReadonly(Boolean isReadonly) {
            this.isReadonly = isReadonly;
        }
        
        public List<Map<String, Object>> getFields() {
            return fields;
        }
        
        public void setFields(List<Map<String, Object>> fields) {
            this.fields = fields;
        }

        public List<Map<String, Object>> getNodes() {
            return nodes;
        }

        public void setNodes(List<Map<String, Object>> nodes) {
            this.nodes = nodes;
        }
    }
    
    /**
     * 流程实例信息DTO（任务详情中展示的流程实例信息）
     */
    public static class ProcessInstanceDTO {
        /** 流程实例ID */
        private String processInstanceId;
        /** 流程名称 */
        private String processName;
        /** 发起人ID */
        private String startUserId;
        /** 发起人姓名 */
        private String startUserName;
        /** 业务Key */
        private String businessKey;
        /** 发起时间 */
        private String startTime;
        
        public String getProcessInstanceId() {
            return processInstanceId;
        }
        
        public void setProcessInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
        }
        
        public String getProcessName() {
            return processName;
        }
        
        public void setProcessName(String processName) {
            this.processName = processName;
        }
        
        public String getStartUserId() {
            return startUserId;
        }
        
        public void setStartUserId(String startUserId) {
            this.startUserId = startUserId;
        }
        
        public String getStartUserName() {
            return startUserName;
        }
        
        public void setStartUserName(String startUserName) {
            this.startUserName = startUserName;
        }
        
        public String getBusinessKey() {
            return businessKey;
        }
        
        public void setBusinessKey(String businessKey) {
            this.businessKey = businessKey;
        }
        
        public String getStartTime() {
            return startTime;
        }
        
        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }
    }
}
