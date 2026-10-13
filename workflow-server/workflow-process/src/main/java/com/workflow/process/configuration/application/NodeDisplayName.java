package com.workflow.process.configuration.application;

/** 节点配置的显示名称规则，供 BPMN 同步与直接保存使用。 */
public final class NodeDisplayName {
    private NodeDisplayName() { }

    /**
     * BPMN 允许节点不命名，但配置与审计需要可定位的显示名称；空白名称使用合法节点 ID。
     * 有意义的名称原样保留，避免保存流程时更改用户输入。调用方须先提供有效的 nodeId。
     */
    public static String resolve(String nodeId, String name) {
        return name == null || name.isBlank() ? nodeId : name;
    }
}
