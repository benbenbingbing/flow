package com.workflow.entity.definition.application.code;

import com.workflow.contracts.entity.code.spi.EntityCodeGeneratorProvider;
import com.workflow.contracts.entity.code.EntityCodeSnapshots;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按稳定标识注册业务生成器；重复标识启动失败，不允许由 Bean 顺序决定实际实现。 */
@Component
public class EntityCodeGeneratorRegistry {
    private final Map<String, EntityCodeGeneratorProvider> generators = new LinkedHashMap<>();
    private final UiExtensionDefinitionValidator validator;

    public EntityCodeGeneratorRegistry(List<EntityCodeGeneratorProvider> implementations,
            UiExtensionDefinitionValidator validator) {
        this.validator = validator;
        for (EntityCodeGeneratorProvider generator : implementations) {
            String code = generator.getCode();
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw new IllegalStateException("编码生成器标识不合法: " + code);
            }
            if (generators.putIfAbsent(code, generator) != null) {
                throw new IllegalStateException("编码生成器重复注册: " + code);
            }
            validator.validateSchemaDefinition(generator.configurationSchema(), "编码生成器参数结构");
        }
    }

    /** 校验实体适用范围和参数，供配置保存、迁移预检、预览及运行时共同使用。 */
    public EntityCodeGeneratorProvider require(String code, String entityCode, Map<String, Object> configuration) {
        EntityCodeGeneratorProvider generator = generators.get(code);
        if (generator == null) throw new IllegalArgumentException("编码生成器未安装: " + code);
        if (!supports(generator, entityCode)) throw new IllegalArgumentException("编码生成器不支持实体: " + entityCode);
        Map<String, Object> config = EntityCodeSnapshots.copy(configuration);
        validator.validateSchemaValue(generator.configurationSchema(), config, "编码生成器参数");
        generator.validateConfiguration(entityCode, config);
        return generator;
    }

    /** 管理页面只展示当前实体可用的生成器，不暴露实现类名。 */
    public List<Option> options(String entityCode) {
        return generators.values().stream().filter(generator -> supports(generator, entityCode))
                .map(generator -> new Option(generator.getCode(), generator.getDisplayName(),
                        generator.configurationSchema())).toList();
    }

    private boolean supports(EntityCodeGeneratorProvider generator, String entityCode) {
        return generator.supportedEntityCodes().isEmpty() || generator.supportedEntityCodes().contains(entityCode);
    }

    public record Option(String code, String displayName, Map<String, Object> configurationSchema) { }
}
