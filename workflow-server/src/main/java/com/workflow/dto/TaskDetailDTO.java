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
    
    public ProcessInstanceDTO getProcessInstance() {
        return processInstance;
    }
    
    public void setProcessInstance(ProcessInstanceDTO processInstance) {
        this.processInstance = processInstance;
    }
    
    public static class FormConfigDTO {
        private String formKey;
        private String entityFormId;
        private String formName;
        private String layoutType;
        private Boolean isReadonly;
        private List<Map<String, Object>> fields;
        
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
    }
    
    public static class ProcessInstanceDTO {
        private String processInstanceId;
        private String processName;
        private String startUserId;
        private String startUserName;
        private String businessKey;
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
