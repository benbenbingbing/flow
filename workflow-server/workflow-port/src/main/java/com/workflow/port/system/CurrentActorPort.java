package com.workflow.port.system;

import com.workflow.port.system.model.identity.CurrentActor;

/** 读取当前操作人并检查其管理员身份及业务权限。 */
public interface CurrentActorPort {
    /**
     * 返回当前请求绑定的操作人。
     *
     * @return 当前操作人
     */
    CurrentActor current();

    /**
     * 判断当前调用者是否具备平台管理员权限，供文件等资源的所有权策略使用。
     * 未认证调用者应由实现方按现有授权策略处理，不得默认授予管理员权限。
     * @return 当前调用者是否为管理员
     */
    boolean isAdministrator();

    /** 判断当前身份是否拥有明确的业务权限码。调用方仍须校验目标对象的数据范围。 */
    default boolean hasPermission(String permissionCode) {
        return false;
    }
}
