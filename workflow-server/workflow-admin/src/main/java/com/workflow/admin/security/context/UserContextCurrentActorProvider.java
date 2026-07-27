package com.workflow.admin.security.context;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import org.springframework.stereotype.Component;

/**
 * 使用现有 UserContext 适配跨模块当前操作人端口。
 */
@Component
public class UserContextCurrentActorProvider implements CurrentActorProvider {

    @Override
    public CurrentActor current() {
        return new CurrentActor(UserContext.getUserId(), UserContext.getUsername());
    }
}
