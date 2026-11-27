package com.workflow.process.instance.api.response;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** 首屏表单读取结果。流程图、审批历史和轮次由 progress 单独提供，避免查看数据时全量查历史。 */
@Data
public class ProcessFormRuntimeDTO {
    /** 实例和实际部署版本供后续请求重新鉴权及诊断，不能作为客户端授权凭据。 */
    private String processInstanceId;
    private String processDefinitionId;
    private Integer processVersion;
    private String processKey;
    private String processName;
    /** 当前所查看实例的生命周期，不代替实体最新一轮流程状态或任务办理能力。 */
    private String status;
    /** 当前任务用于审批定位；加签任务只加入已校验属于当前用户的请求任务。 */
    private List<ProcessProgressDTO.TaskInfoDTO> tasks = List.of();
    /** 已授权的实体聚合，系统字段与业务字段展平供首屏回显；后续写入仍须重新校验权限。 */
    private Map<String, Object> entityData;

    /** 实例发布上下文解析出的单个生效表单，用于回显及提交前的配置摘要核对。 */
    private FormConfigDTO formConfig;

    /** 目标节点的意见和动作选项，前端据此展示审批区，提交时由服务端再次校验。 */
    private ApprovalConfigDTO approvalConfig;

    /**
     * 审批配置DTO
     */
    @Data
    public static class ApprovalConfigDTO {
        /**
         * 是否启用审批意见
         */
        private Boolean enabled;

        /**
         * 审批意见名称
         */
        private String commentLabel;

        /**
         * 审批选项列表
         */
        private List<ApprovalOptionDTO> options;
    }

    /**
     * 审批选项DTO
     */
    @Data
    public static class ApprovalOptionDTO {
        /**
         * 选项值
         */
        private String value;

        /**
         * 选项显示名称
         */
        private String label;

        /**
         * 按钮样式类型
         */
        private String type;

        /**
         * 是否显示审批备注
         */
        private Boolean showComment;
    }

    /**
     * 表单配置DTO
     */
    @Data
    public static class FormConfigDTO {
        /** 页面加载时的依赖摘要，仅用于提交前检查配置变更，不代表访问权限。 */
        private String configHash;
        /** 下次请求重新鉴权的任务/实例定位信息，不保存客户端授权结果。 */
        private Map<String, Object> runtimeContext;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private Map<String, Object> contextValues;

        /**
         * 表单ID
         */
        private String formId;

        /**
         * 表单名称
         */
        private String formName;

        /**
         * 表单Key
         */
        private String formKey;

        /**
         * 布局类型
         */
        private String layoutType;

        private Boolean isReadonly;

        private String customComponent;

        private String viewConfig;

        /**
         * 表单级统一数据源绑定文档。
         * 审批页跳过仅用于新增的 FORM_INIT，执行 AFTER_LOAD 和允许的浏览器预校验；
         * BEFORE_SUBMIT 最终仍由服务端权威执行。
         */
        private String dataSourceBindingsDocument;

        /**
         * 表单字段列表
         */
        private List<Map<String, Object>> fields;

        /**
         * 递归表单节点列表
         */
        private List<Map<String, Object>> nodes;
    }

}
