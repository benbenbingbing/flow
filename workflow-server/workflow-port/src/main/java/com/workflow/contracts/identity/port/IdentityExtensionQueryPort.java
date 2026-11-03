package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.model.*;
import java.util.Optional;

/** 查询已注册且当前调用者有权读取的资料；目标必须按本地 ID 精确解释。 */
public interface IdentityExtensionQueryPort {
    /** 未建档时为空；未知类型、未授权或对象不可见时抛边界异常。 */
    <T extends UserExtension> Optional<T> findUserExtension(String userId, Class<T> extensionType);
    /** 组织版本的精确 ID 查询，不接受客户端任意类名。 */
    <T extends OrganizationExtension> Optional<T> findOrganizationExtension(String organizationId, Class<T> extensionType);
}
