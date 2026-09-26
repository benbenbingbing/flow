package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.list.spi.EntityListContextResolverProvider;
import com.workflow.contracts.entity.list.model.EntityListRuntimeContext;
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
        implements EntityListContextResolverProvider {

    public static final String RELATION_KEY =
            "projectCustomRelation";

    /**
     * 读取关系键；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的关系键文本，供调用方比较或展示
     */
    @Override
    public String getRelationKey() {
        return RELATION_KEY;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "项目自定义关联上下文";
    }

    /**
     * 解析项目自定义实体列表上下文解析器；输出作为后续校验或处理的输入。
     *
     * @param context 执行上下文，向后续项目自定义实体列表上下文解析器步骤传递身份、配置或状态
     * @return 项目自定义实体列表上下文解析器键值结果，供调用方继续处理
     */
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
