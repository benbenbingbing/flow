package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.list.spi.DataScopePredicateProvider;
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

    /**
     * 读取类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的类型文本，供调用方比较或展示
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "项目自定义数据范围";
    }

    /**
     * 读取描述；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的描述文本，供调用方比较或展示
     */
    @Override
    public String getDescription() {
        return "安全演示条件：固定返回空数据范围。";
    }

    /**
     * 读取{@code supported}字段类型集合；查询结果供调用方展示或继续处理。
     *
     * @return 项目自定义数据作用域判断条件提供者集合，供调用方遍历或展示
     */
    @Override
    public List<String> getSupportedFieldTypes() {
        return List.of("ANY");
    }

    /**
     * 读取配置结构；查询结果供调用方展示或继续处理。
     *
     * @return 配置结构键值结果，供调用方继续处理
     */
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

    /**
     * 校验项目自定义数据作用域判断条件提供者；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param config 配置内容，决定后续项目自定义数据作用域判断条件提供者的处理规则
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 编译项目自定义数据作用域判断条件提供者；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param config 配置内容，决定后续项目自定义数据作用域判断条件提供者的处理规则
     * @param userContext 执行上下文，向后续项目自定义数据作用域判断条件提供者步骤传递身份、配置或状态
     * @return 编译后的项目自定义数据作用域判断条件提供者结果，供调用方继续处理
     */
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
