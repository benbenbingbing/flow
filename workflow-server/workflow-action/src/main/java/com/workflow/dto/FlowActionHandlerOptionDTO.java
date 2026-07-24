package com.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 流程动作处理器选项 DTO。
 *
 * <p>供前端展示可选处理器列表，包含 Bean 名称、类名、中文展示名、可见范围、
 * 实体绑定、能力声明（支持的触发时机与执行方式）等信息。</p>
 */
@Data
public class FlowActionHandlerOptionDTO {

    /** 动作定义 ID；未配置时为空 */
    private String definitionId;
    /** 动作编码 */
    private String actionCode;
    /** 处理器 Bean 名称 */
    private String beanName;
    /** 处理器实现类全名 */
    private String className;
    /** 中文展示名 */
    private String displayName;
    /** 动作描述 */
    private String description;
    /** 可见范围：GLOBAL、ENTITY */
    private String visibilityScope;
    /** 可见实体编码列表 */
    private List<String> entityCodes;
    /** 是否启用 */
    private Boolean enabled;
    /** 是否已在目录中配置 */
    private Boolean configured;
    /** Bean 是否可用（容器中是否存在） */
    private Boolean available;
    /** 是否为类型化处理器 */
    private Boolean typed;
    /** 类型化处理器的参数类型全名 */
    private String paramType;
    /** 处理器支持的触发时机集合；空集合表示支持全部 */
    private Set<String> supportedTriggerTimings;
    /** 处理器支持的执行方式集合；空集合表示支持全部 */
    private Set<String> supportedExecutionModes;
    /** 处理器推荐的执行方式 */
    private String recommendedExecutionMode;
}
