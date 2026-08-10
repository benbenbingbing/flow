package com.workflow.project.custom;

import com.workflow.contracts.entity.list.EntityListContextResolver;
import com.workflow.contracts.entity.list.EntityListRuntimeContext;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 实体列表可信上下文解析示例。
 *
 * <p>关联关系 Key 为 {@value #RELATION_KEY}。真实实现应从服务端读取来源记录，
 * 再生成可信过滤条件；示例不会信任客户端附加参数，因此返回空条件。</p>
 */
@Slf4j
@Component
public class ProjectCustomEntityListContextResolver
        implements EntityListContextResolver {

    public static final String RELATION_KEY =
            "projectCustomRelation";

    @Override
    public String getRelationKey() {
        return RELATION_KEY;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义关联上下文";
    }

    @Override
    public Map<String, Object> resolve(
            EntityListRuntimeContext context) {
        log.info(
                "项目列表上下文解析执行: relationKey={}, sourceEntityCode={}, sourceRecordId={}, targetEntityCode={}, listKey={}, parameterKeys={}",
                RELATION_KEY,
                LogValue.safe(context == null
                        ? null : context.sourceEntityCode()),
                LogValue.safe(context == null
                        ? null : context.sourceRecordId()),
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                context == null
                        || context.parameters() == null
                        ? java.util.Set.of()
                        : context.parameters().keySet());
        return Map.of();
    }
}
