package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.code.EntityCodeGenerationContext;
import com.workflow.contracts.entity.code.spi.EntityCodeGeneratorProvider;
import com.workflow.contracts.entity.code.EntityCodePreviewContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 项目编码扩展示例：使用预分配记录 ID 保证多实例安全，无需查询尚未插入的项目记录。 */
@Component
public class ProjectEntityCodeGenerator implements EntityCodeGeneratorProvider {
    @Override public String getCode() { return "PROJECT_RECORD_ID"; }
    @Override public String getDisplayName() { return "项目编号（前缀 + 记录ID）"; }
    @Override public Set<String> supportedEntityCodes() { return Set.of("project"); }

    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of("type", "object", "required", List.of("prefix"), "additionalProperties", false,
                "properties", Map.of("prefix", Map.of("type", "string", "title", "编号前缀",
                        "description", "使用业务前缀区分项目来源，例如 XM。", "default", "XM")));
    }

    @Override
    public void validateConfiguration(String entityCode, Map<String, Object> configuration) {
        Object prefix = configuration.get("prefix");
        if (!(prefix instanceof String text) || !text.matches("[A-Z0-9_]{1,20}")) {
            throw new IllegalArgumentException("项目前缀需为1到20位大写字母、数字或下划线");
        }
    }

    @Override
    public String generate(EntityCodeGenerationContext context, Map<String, Object> configuration) {
        // recordId 尚未落库，直接作为编号的一部分；业务字段可从 context.data() 读取。
        return configuration.get("prefix") + "-" + context.recordId();
    }

    @Override
    public Optional<String> preview(EntityCodePreviewContext context, Map<String, Object> configuration) {
        return Optional.of(configuration.get("prefix") + "-0123456789abcdef0123456789abcdef");
    }
}
