package com.workflow.migration.application;

import com.workflow.contracts.identity.port.OrganizationPositionDirectoryPort;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import com.workflow.contracts.process.assignment.spi.PersonResolverConfigurationValidator;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.assignment.entity.EntityUserReferenceFieldConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.workflow.migration.application.ConfigMigrationAssignmentSupport.*;

/** 导入目标的动态人员依赖校验；只验证目录和静态配置，不运行解析器获取人员。 */
@Component
@RequiredArgsConstructor
class ConfigMigrationAssignmentTargetValidator {
    private final PersonResolverRuntimeService resolverRuntimeService;
    private final OrganizationPositionDirectoryPort organizationDirectory;
    private final List<PersonResolverConfigurationValidator> configurationValidators;

    /**
     * 检查岗位、层级、人员字段及解析器。字段查询由调用方提供，允许引用同包即将发布的实体。
     * 配置非法时抛出可展示的原因；目录缺失返回 false。
     */
    boolean resolved(Map<String, Object> dependency, String type, String key,
            BiFunction<String, String, String> mapping,
            Function<String, Map<String, Object>> fieldLookup) {
        if ("POSITION".equals(type)) return organizationDirectory.requireEnabledPosition(key) != null;
        if ("ORG_BUSINESS_LEVEL".equals(type)) return organizationDirectory.isOrganizationBusinessLevelEnabled(key);
        if ("ENTITY_USER_FIELD".equals(type)) {
            String[] parts = key.split("/", 2);
            if (parts.length != 2) return false;
            String coordinate = key.equals(dependency.get("key"))
                    ? mapping.apply("ENTITY", parts[0]) + "/" + parts[1] : key;
            return !fieldLookup.apply(coordinate).isEmpty();
        }
        if (!"PERSON_RESOLVER".equals(type)) return false;

        for (Map<String, Object> reference : maps(dependency.get("references"))) {
            PersonResolveUsage usage = PersonResolveUsage.valueOf(text(reference.get("usage")));
            if (!resolverRuntimeService.supportsConfigured(key, usage)) return false;
            // 参数先执行与 BPMN 完全相同的编码映射，再用目标环境的校验器检查。
            Map<String, Object> config = new java.util.LinkedHashMap<>();
            config.put("assigneeType", "interface");
            config.put("resolverCode", dependency.get("key"));
            config.put("assignmentMode", reference.get("assignmentMode"));
            config.put("extraParams", reference.get("extraParams"));
            String mapped = rewriteNodeConfig(write(Map.of("assigneeConfig", config)), reference,
                    (refType, refKey, context) -> mapping.apply(refType, refKey));
            Map<String, Object> params = object(object(read(mapped).get("assigneeConfig")).get("extraParams"));
            String mode = text(reference.get("assignmentMode"));
            boolean multi = Boolean.TRUE.equals(reference.get("multiInstance"));
            if (EntityUserReferenceFieldConfig.RESOLVER_CODE.equals(key)) {
                // 新实体尚未落库，直接验证包内字段；不能提前调用依赖目标库的字段解析器。
                EntityUserReferenceFieldConfig fieldConfig = EntityUserReferenceFieldConfig.parse(params);
                fieldConfig.validateAssignmentMode(mode, multi);
                Map<String, Object> field = fieldLookup.apply(fieldConfig.entityCode() + "/" + fieldConfig.fieldCode());
                if (field.isEmpty()) return false;
                if (usage == PersonResolveUsage.ASSIGNEE) {
                    fieldConfig.validateFieldCardinality(mode, multi,
                            "MULTI_REFERENCE".equals(text(field.get("fieldType"))));
                }
            } else {
                boolean validated = false;
                for (PersonResolverConfigurationValidator validator : configurationValidators) {
                    if (key.equals(validator.resolverCode())) {
                        validator.validate(new PersonResolverConfigurationValidationRequest(
                                usage, mode, multi, params));
                        validated = true;
                    }
                }
                if ("relativeOrgPosition".equals(key) && !validated) {
                    throw new IllegalArgumentException("相对组织职务的配置校验器未注册");
                }
            }
        }
        // 新快照必须有用途及配置上下文，防止不完整的解析器依赖静默通过。
        return !maps(dependency.get("references")).isEmpty();
    }
}
