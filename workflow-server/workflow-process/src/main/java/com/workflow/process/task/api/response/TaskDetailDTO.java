package com.workflow.process.task.api.response;

import com.workflow.process.form.api.response.FormConfigDTO;

import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
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
     * 表单配置列表兼容字段，当前最多包含一个表单。
     */
    private List<FormConfigDTO> formConfigs;
    
    /**
     * 流程实例信息
     */
    private ProcessInstanceDTO processInstance;
    
    // Getter 和 Setter 方法
    /**
     * 读取流程任务；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的流程任务结果，供调用方继续处理
     */
    public ProcessTask getProcessTask() {
        return processTask;
    }
    
    /**
     * 设置流程任务；后续读取或执行将使用更新后的状态。
     *
     * @param processTask 流程任务，供本方法设置流程任务时使用
     */
    public void setProcessTask(ProcessTask processTask) {
        this.processTask = processTask;
    }
    
    /**
     * 读取实体数据；查询结果供调用方展示或继续处理。
     *
     * @return 实体数据键值结果，供调用方继续处理
     */
    public Map<String, Object> getEntityData() {
        return entityData;
    }
    
    /**
     * 设置实体数据；后续读取或执行将使用更新后的状态。
     *
     * @param entityData 实体数据，供本方法设置实体数据时使用
     */
    public void setEntityData(Map<String, Object> entityData) {
        this.entityData = entityData;
    }
    
    /**
     * 读取字段名称映射；查询结果供调用方展示或继续处理。
     *
     * @return 字段名称映射键值结果，供调用方继续处理
     */
    public Map<String, String> getFieldNameMap() {
        return fieldNameMap;
    }
    
    /**
     * 设置字段名称映射；后续读取或执行将使用更新后的状态。
     *
     * @param fieldNameMap 字段名称映射，供本方法设置字段名称映射时使用
     */
    public void setFieldNameMap(Map<String, String> fieldNameMap) {
        this.fieldNameMap = fieldNameMap;
    }
    
    /**
     * 读取表单配置；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的表单配置结果，供调用方继续处理
     */
    public FormConfigDTO getFormConfig() {
        return formConfig;
    }
    
    /**
     * 设置表单配置；后续读取或执行将使用更新后的状态。
     *
     * @param formConfig 表单配置内容，决定后续表单配置的处理规则
     */
    public void setFormConfig(FormConfigDTO formConfig) {
        this.formConfig = formConfig;
    }

    /**
     * 读取表单{@code configs}；查询结果供调用方展示或继续处理。
     *
     * @return 表单配置集合，供调用方遍历或展示
     */
    public List<FormConfigDTO> getFormConfigs() {
        return formConfigs;
    }

    /**
     * 设置表单{@code configs}；后续读取或执行将使用更新后的状态。
     *
     * @param formConfigs 表单{@code configs}，供本方法设置表单{@code configs}时使用
     */
    public void setFormConfigs(List<FormConfigDTO> formConfigs) {
        this.formConfigs = formConfigs == null || formConfigs.isEmpty()
                ? List.of()
                : List.of(formConfigs.get(0));
    }
    
    /**
     * 读取流程实例；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的流程实例结果，供调用方继续处理
     */
    public ProcessInstanceDTO getProcessInstance() {
        return processInstance;
    }
    
    /**
     * 设置流程实例；后续读取或执行将使用更新后的状态。
     *
     * @param processInstance 流程实例，供本方法设置流程实例时使用
     */
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
        /** 实际生效的热修复发布ID */
        private String effectiveFormReleaseId;
        /** 是否应用兼容热修复 */
        private Boolean hotfixApplied;
        /** 嵌套表单解析上下文令牌 */
        private String releaseResolutionToken;
        /** 表单名称 */
        private String formName;
        /** 布局类型 */
        private String layoutType;
        /** 是否只读 */
        private Boolean isReadonly;
        /** 表单级统一数据源绑定文档 */
        private String dataSourceBindingsDocument;
        /** 表单字段列表 */
        private List<Map<String, Object>> fields;
        /** 表单节点列表（递归结构） */
        private List<Map<String, Object>> nodes;
        
        /**
         * 读取表单键；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的表单键文本，供调用方比较或展示
         */
        public String getFormKey() {
            return formKey;
        }
        
        /**
         * 设置表单键；后续读取或执行将使用更新后的状态。
         *
         * @param formKey 表单键，后续用于授权校验、关联或幂等去重
         */
        public void setFormKey(String formKey) {
            this.formKey = formKey;
        }
        
        /**
         * 读取实体表单ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的实体表单ID文本，供调用方比较或展示
         */
        public String getEntityFormId() {
            return entityFormId;
        }
        
        /**
         * 设置实体表单ID；后续读取或执行将使用更新后的状态。
         *
         * @param entityFormId 实体表单ID，后续用于设置实体表单ID时定位或关联目标
         */
        public void setEntityFormId(String entityFormId) {
            this.entityFormId = entityFormId;
        }

        /**
         * 读取表单发布版本ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的表单发布版本ID文本，供调用方比较或展示
         */
        public String getFormReleaseId() {
            return formReleaseId;
        }

        /**
         * 设置表单发布版本ID；后续读取或执行将使用更新后的状态。
         *
         * @param formReleaseId 表单发布版本ID，后续用于设置表单发布版本ID时定位或关联目标
         */
        public void setFormReleaseId(String formReleaseId) {
            this.formReleaseId = formReleaseId;
        }

        /**
         * 读取表单发布版本；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的表单配置DTO结果，供调用方继续处理
         */
        public Integer getFormReleaseVersion() {
            return formReleaseVersion;
        }

        /**
         * 设置表单发布版本；后续读取或执行将使用更新后的状态。
         *
         * @param formReleaseVersion 表单发布版本，供本方法设置表单发布版本时使用
         */
        public void setFormReleaseVersion(Integer formReleaseVersion) {
            this.formReleaseVersion = formReleaseVersion;
        }

        /**
         * 读取有效表单发布版本ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的有效表单发布版本ID文本，供调用方比较或展示
         */
        public String getEffectiveFormReleaseId() {
            return effectiveFormReleaseId;
        }

        /**
         * 设置有效表单发布版本ID；后续读取或执行将使用更新后的状态。
         *
         * @param effectiveFormReleaseId 有效表单发布版本ID，后续用于设置有效表单发布版本ID时定位或关联目标
         */
        public void setEffectiveFormReleaseId(
                String effectiveFormReleaseId) {
            this.effectiveFormReleaseId = effectiveFormReleaseId;
        }

        /**
         * 读取热修复{@code applied}；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的表单配置DTO结果，供调用方继续处理
         */
        public Boolean getHotfixApplied() {
            return hotfixApplied;
        }

        /**
         * 设置热修复{@code applied}；后续读取或执行将使用更新后的状态。
         *
         * @param hotfixApplied 热修复{@code applied}，供本方法设置热修复{@code applied}时使用
         */
        public void setHotfixApplied(Boolean hotfixApplied) {
            this.hotfixApplied = hotfixApplied;
        }

        /**
         * 读取发布版本解析令牌；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的发布版本解析令牌文本，供调用方比较或展示
         */
        public String getReleaseResolutionToken() {
            return releaseResolutionToken;
        }

        /**
         * 设置发布版本解析令牌；后续读取或执行将使用更新后的状态。
         *
         * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
         */
        public void setReleaseResolutionToken(
                String releaseResolutionToken) {
            this.releaseResolutionToken = releaseResolutionToken;
        }
        
        /**
         * 读取表单名称；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的表单名称文本，供调用方比较或展示
         */
        public String getFormName() {
            return formName;
        }
        
        /**
         * 设置表单名称；后续读取或执行将使用更新后的状态。
         *
         * @param formName 表单名称，后续用于设置表单名称时匹配或展示
         */
        public void setFormName(String formName) {
            this.formName = formName;
        }
        
        /**
         * 读取{@code layout}类型；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的{@code layout}类型文本，供调用方比较或展示
         */
        public String getLayoutType() {
            return layoutType;
        }
        
        /**
         * 设置{@code layout}类型；后续读取或执行将使用更新后的状态。
         *
         * @param layoutType {@code layout}类型标识，决定后续{@code layout}类型采用的处理分支
         */
        public void setLayoutType(String layoutType) {
            this.layoutType = layoutType;
        }
        
        /**
         * 读取是否{@code readonly}；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的表单配置DTO结果，供调用方继续处理
         */
        public Boolean getIsReadonly() {
            return isReadonly;
        }
        
        /**
         * 设置是否{@code readonly}；后续读取或执行将使用更新后的状态。
         *
         * @param isReadonly 是否{@code readonly}，供本方法设置是否{@code readonly}时使用
         */
        public void setIsReadonly(Boolean isReadonly) {
            this.isReadonly = isReadonly;
        }

        /**
         * 读取数据来源绑定集合文档；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的数据来源绑定集合文档文本，供调用方比较或展示
         */
        public String getDataSourceBindingsDocument() {
            return dataSourceBindingsDocument;
        }

        /**
         * 设置数据来源绑定集合文档；后续读取或执行将使用更新后的状态。
         *
         * @param dataSourceBindingsDocument 数据来源绑定集合文档，供本方法设置数据来源绑定集合文档时使用
         */
        public void setDataSourceBindingsDocument(
                String dataSourceBindingsDocument) {
            this.dataSourceBindingsDocument = dataSourceBindingsDocument;
        }
        
        /**
         * 读取字段；查询结果供调用方展示或继续处理。
         *
         * @return 表单配置DTO集合，供调用方遍历或展示
         */
        public List<Map<String, Object>> getFields() {
            return fields;
        }
        
        /**
         * 设置字段；后续读取或执行将使用更新后的状态。
         *
         * @param fields 字段集合，后续逐项校验、转换或持久化
         */
        public void setFields(List<Map<String, Object>> fields) {
            this.fields = fields;
        }

        /**
         * 读取节点集合；查询结果供调用方展示或继续处理。
         *
         * @return 表单配置DTO集合，供调用方遍历或展示
         */
        public List<Map<String, Object>> getNodes() {
            return nodes;
        }

        /**
         * 设置节点集合；后续读取或执行将使用更新后的状态。
         *
         * @param nodes 节点集合，供本方法设置节点集合时使用
         */
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
        
        /**
         * 读取流程实例ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的流程实例ID文本，供调用方比较或展示
         */
        public String getProcessInstanceId() {
            return processInstanceId;
        }
        
        /**
         * 设置流程实例ID；后续读取或执行将使用更新后的状态。
         *
         * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
         */
        public void setProcessInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
        }
        
        /**
         * 读取流程名称；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的流程名称文本，供调用方比较或展示
         */
        public String getProcessName() {
            return processName;
        }
        
        /**
         * 设置流程名称；后续读取或执行将使用更新后的状态。
         *
         * @param processName 流程名称，后续用于设置流程名称时匹配或展示
         */
        public void setProcessName(String processName) {
            this.processName = processName;
        }
        
        /**
         * 读取启动用户ID；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的启动用户ID文本，供调用方比较或展示
         */
        public String getStartUserId() {
            return startUserId;
        }
        
        /**
         * 设置启动用户ID；后续读取或执行将使用更新后的状态。
         *
         * @param startUserId 启动用户ID，后续用于设置启动用户ID时定位或关联目标
         */
        public void setStartUserId(String startUserId) {
            this.startUserId = startUserId;
        }
        
        /**
         * 读取启动用户名称；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的启动用户名称文本，供调用方比较或展示
         */
        public String getStartUserName() {
            return startUserName;
        }
        
        /**
         * 设置启动用户名称；后续读取或执行将使用更新后的状态。
         *
         * @param startUserName 启动用户名称，后续用于设置启动用户名称时匹配或展示
         */
        public void setStartUserName(String startUserName) {
            this.startUserName = startUserName;
        }
        
        /**
         * 读取业务键；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的业务键文本，供调用方比较或展示
         */
        public String getBusinessKey() {
            return businessKey;
        }
        
        /**
         * 设置业务键；后续读取或执行将使用更新后的状态。
         *
         * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
         */
        public void setBusinessKey(String businessKey) {
            this.businessKey = businessKey;
        }
        
        /**
         * 读取启动时间；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的启动时间文本，供调用方比较或展示
         */
        public String getStartTime() {
            return startTime;
        }
        
        /**
         * 设置启动时间；后续读取或执行将使用更新后的状态。
         *
         * @param startTime 启动时间，后续用于判断有效期或展示该事件的发生时间
         */
        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }
    }
}
