package com.workflow.project.custom;

import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义人员解析器示例。
 *
 * <p>前端可配置 {@code userKeys}，也可启用
 * {@code fallbackToInitiator} 回退到流程发起人。支持办理人、候选人、
 * 多实例和知会四类人员场景。</p>
 */
@Slf4j
@Component
public class ProjectCustomPersonResolver
        implements PersonResolver {

    public static final String CODE =
            "projectCustomPersonResolver";

    /** 显式配置的用户 ID 或用户名集合，解析结果会去空、去重并保持配置顺序。 */
    private static final String USER_KEYS =
            "userKeys";

    /** 当 {@link #USER_KEYS} 未解析出人员时，是否补入流程发起人；默认开启。 */
    private static final String FALLBACK_TO_INITIATOR =
            "fallbackToInitiator";

    private static final PersonResolverDescriptor DESCRIPTOR =
            new PersonResolverDescriptor(
                    CODE,
                    "项目自定义人员解析器",
                    "按 userKeys 返回人员；未配置时可回退流程发起人。",
                    1,
                    1,
                    Set.of(PersonResolveUsage.values()),
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    USER_KEYS, Map.of(
                                            "type", "array",
                                            "title", "用户ID或用户名",
                                            "items", Map.of(
                                                    "type", "string")),
                                    FALLBACK_TO_INITIATOR, Map.of(
                                            "type", "boolean",
                                            "title", "无配置时回退发起人"))),
                    false);

    @Override
    public PersonResolverDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PersonResolveResult resolve(
            PersonResolveRequest request) {
        LinkedHashSet<String> userKeys =
                new LinkedHashSet<>();
        Object configured =
                request.extraParams().get(USER_KEYS);
        if (configured instanceof Collection<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(userKeys::add);
        }
        boolean fallback = Boolean.parseBoolean(
                String.valueOf(request.extraParams()
                        .getOrDefault(
                                FALLBACK_TO_INITIATOR,
                                true)));
        if (userKeys.isEmpty() && fallback
                && StringUtils.hasText(
                        request.initiatorId())) {
            userKeys.add(request.initiatorId());
        }
        log.info(
                "项目自定义人员解析完成: resolverCode={}, usage={}, processInstanceId={}, nodeId={}, configuredCount={}, resultCount={}, fallbackToInitiator={}",
                CODE,
                LogValue.safe(request.usage()),
                LogValue.safe(request.processInstanceId()),
                LogValue.safe(request.nodeId()),
                configured instanceof Collection<?> values
                        ? values.size() : 0,
                userKeys.size(),
                fallback);
        return userKeys.isEmpty()
                ? new PersonResolveResult(
                        List.of(),
                        List.of("项目自定义人员解析器未解析到人员"))
                : PersonResolveResult.users(
                        List.copyOf(userKeys));
    }
}
