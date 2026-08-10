package com.workflow.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.process.cc.application.CcRecipientResolver;
import com.workflow.process.cc.application.CcRuntimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 流程知会人员解析器兼容示例。
 *
 * <p>编码为 {@value #CODE}。优先返回参数中的 {@code userKeys}，未配置时可用
 * {@code fallbackToOperator=true} 回退到当前操作人。新配置优先使用统一的
 * {@link ProjectCustomPersonResolver}。</p>
 */
@Slf4j
@Component
public class ProjectCustomCcRecipientResolver
        implements CcRecipientResolver {

    public static final String CODE =
            "projectCustomCcRecipient";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<String> resolve(
            CcRuntimeContext context,
            Map<String, Object> parameters) {
        Map<String, Object> safeParameters =
                parameters == null
                        ? Map.of() : parameters;
        LinkedHashSet<String> recipients =
                new LinkedHashSet<>();
        Object configured =
                safeParameters.get("userKeys");
        if (configured instanceof Collection<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(recipients::add);
        }
        boolean fallback = Boolean.parseBoolean(
                String.valueOf(safeParameters
                        .getOrDefault(
                                "fallbackToOperator",
                                true)));
        if (recipients.isEmpty() && fallback
                && context != null
                && StringUtils.hasText(
                        context.operatorId())) {
            recipients.add(context.operatorId());
        }
        log.info(
                "项目知会人员解析完成: resolverCode={}, processInstanceId={}, nodeId={}, timing={}, configuredCount={}, resultCount={}, fallbackToOperator={}",
                CODE,
                LogValue.safe(context == null
                        ? null : context.processInstanceId()),
                LogValue.safe(context == null
                        ? null : context.nodeId()),
                LogValue.safe(context == null
                        ? null : context.timing()),
                configured instanceof Collection<?> values
                        ? values.size() : 0,
                recipients.size(),
                fallback);
        return List.copyOf(recipients);
    }
}
