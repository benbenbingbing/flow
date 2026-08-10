package com.workflow.project.custom;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.entity.list.DataScopePredicateProvider;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 自定义数据范围条件示例。
 *
 * <p>类型为 {@value #TYPE}。示例固定编译为 {@code 1 = 0}，
 * 因而不会意外放大数据权限；接入真实业务时应仅返回参数化 SQL。</p>
 */
@Slf4j
@Component
public class ProjectCustomDataScopePredicateProvider
        implements DataScopePredicateProvider {

    public static final String TYPE =
            "PROJECT_CUSTOM_SCOPE";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义数据范围";
    }

    @Override
    public String getDescription() {
        return "安全演示条件：固定返回空数据范围。";
    }

    @Override
    public List<String> getSupportedFieldTypes() {
        return List.of("ANY");
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
                "mode", Map.of(
                        "label", "演示模式",
                        "type", "select",
                        "required", true,
                        "defaultValue", "EMPTY_RESULT",
                        "options", List.of("EMPTY_RESULT")));
    }

    @Override
    public void validate(
            String entityCode,
            Map<String, Object> config) {
        Object mode = config == null
                ? null : config.get("mode");
        if (mode != null
                && !"EMPTY_RESULT".equalsIgnoreCase(
                        String.valueOf(mode))) {
            throw new IllegalArgumentException(
                    "项目数据范围示例仅支持 EMPTY_RESULT");
        }
    }

    @Override
    public DataScopePlan compile(
            String entityCode,
            Map<String, Object> config,
            Map<String, Object> userContext) {
        validate(entityCode, config);
        log.info(
                "项目数据范围条件编译: type={}, entityCode={}, configKeys={}, userContextKeys={}, result=EMPTY",
                TYPE,
                LogValue.safe(entityCode),
                config == null
                        ? java.util.Set.of()
                        : config.keySet(),
                userContext == null
                        ? java.util.Set.of()
                        : userContext.keySet());
        return new DataScopePlan(
                true,
                "1 = 0",
                Map.of(),
                List.of(),
                List.of(TYPE),
                "项目示例数据范围固定返回空结果",
                null);
    }
}
