package com.workflow.entity.definition.application.code;

import com.workflow.contracts.entity.mutation.model.EntityMutationContext;

/**
 * 仅由聚合写入器建立的同步调用作用域，向嵌套子表取号传递可信身份与变更幂等键。
 * 不能从请求字段建立，必须通过 try-with-resources 恢复，避免线程池串用身份。
 */
public final class EntityCodeGenerationScope implements AutoCloseable {
    private static final ThreadLocal<EntityMutationContext> CURRENT = new ThreadLocal<>();
    private final EntityMutationContext previous;

    private EntityCodeGenerationScope(EntityMutationContext context) {
        previous = CURRENT.get();
        CURRENT.set(context);
    }

    public static EntityCodeGenerationScope open(EntityMutationContext context) {
        return new EntityCodeGenerationScope(context);
    }

    public static EntityMutationContext current() { return CURRENT.get(); }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
