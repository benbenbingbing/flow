package com.workflow.admin.security.context;

import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import org.springframework.stereotype.Component;

/**
 * 使用现有认证上下文适配跨模块当前操作人端口。
 *
 * <p>兼容期保留原 Bean 类型与默认 Bean 名，避免已有按实现类类型注入、按 Bean 名查找或
 * 已编译插件发生回归。业务调用方应注入 {@link CurrentActorPort}，不依赖本实现或
 * 静态 UserContext，从而将认证上下文的存储方式限制在管理模块内。</p>
 */
@Component("userContextCurrentActorProvider")
public class UserContextCurrentActorProvider implements CurrentActorPort {

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @return 处理后的当前结果，供调用方继续处理
     */
    @Override
    public CurrentActor current() {
        return new CurrentActor(UserContext.getUserId(), UserContext.getUsername());
    }
}
