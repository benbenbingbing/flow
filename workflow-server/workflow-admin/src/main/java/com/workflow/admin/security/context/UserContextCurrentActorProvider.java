package com.workflow.admin.security.context;

import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import org.springframework.stereotype.Component;

/**
 * 使用现有认证上下文适配跨模块当前操作人端口。
 *
 * <p>兼容期保留原 Bean 类型与默认 Bean 名，避免已有按实现类类型注入、按 Bean 名查找或
 * 已编译插件发生回归。{@link CurrentActorPort} 是 canonical {@code CurrentActorPort}
 * 的子接口，因此新调用方仍可直接注入新端口。</p>
 */
@Component("userContextCurrentActorProvider")
public class UserContextCurrentActorProvider implements CurrentActorPort {

    @Override
    public CurrentActor current() {
        return new CurrentActor(UserContext.getUserId(), UserContext.getUsername());
    }
}
