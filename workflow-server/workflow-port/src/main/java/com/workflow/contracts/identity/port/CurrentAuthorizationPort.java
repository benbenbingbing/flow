package com.workflow.contracts.identity.port;

/** 当前调用者的授权能力；业务模块无需了解角色表或认证上下文的存储方式。 */
public interface CurrentAuthorizationPort {
    /**
     * 判断当前调用者是否具备平台管理员权限，供文件等资源的所有权策略使用。
     * 未认证调用者应由实现方按现有授权策略处理，不得默认授予管理员权限。
     * @return 当前调用者是否为管理员
     */
    boolean isAdministrator();
}
