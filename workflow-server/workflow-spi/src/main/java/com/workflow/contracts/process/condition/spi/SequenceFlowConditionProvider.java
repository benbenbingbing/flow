package com.workflow.contracts.process.condition.spi;

import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.process.condition.model.SequenceFlowConditionRequest;

import java.util.Map;

/**
 * 顺序流业务条件扩展。实现为 Spring Bean 后进入条件接口目录。
 * 接口只做查询和判断，不得写业务数据或推进流程；引擎重试时可能再次调用。
 */
public interface SequenceFlowConditionProvider {

    /** 返回稳定且唯一的业务编码；流程配置绑定此编码，不绑定 Bean 名称。 */
    String getCode();

    /** 返回设计器中供用户选择的接口名称。 */
    String getDisplayName();

    default String getDescription() { return ""; }

    default ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.CUSTOM;
    }

    /** 校验连线参数；发布和执行均调用，参数非法时抛 IllegalArgumentException。 */
    default void validate(Map<String, Object> extraParams) {}

    /**
     * 判断当前分支是否满足业务规则。
     *
     * @param request 当前实例、连线和本次提交变量的不可变快照
     * @return true 表示条件命中，false 表示不走该分支；网关仍遵守自身的选路规则
     * @throws RuntimeException 查询或计算失败时向外抛出，使本次办理回滚，不能把失败当作 false
     */
    boolean evaluate(SequenceFlowConditionRequest request);
}
