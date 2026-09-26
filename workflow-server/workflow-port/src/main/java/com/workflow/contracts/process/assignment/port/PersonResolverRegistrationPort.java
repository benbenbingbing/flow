package com.workflow.contracts.process.assignment.port;

/** 人员解析扩展的受控目录状态；流程只消费启用结果，目录存储由管理模块负责。 */
public interface PersonResolverRegistrationPort {
    /** 按解析器编码查询有效目录项；不存在、已删除或未启用均返回 false。 */
    boolean isEnabled(String resolverCode);
}
