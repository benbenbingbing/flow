package com.workflow.process.assignment.entity;

import com.workflow.contracts.entity.port.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.contracts.entity.port.EntityUserReferencePort.UserReferenceField;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.entity.port.EntityUserReferencePort;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolutionException;
import com.workflow.contracts.process.assignment.spi.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;
import com.workflow.contracts.process.assignment.spi.PersonResolverConfigurationValidator;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从流程绑定实体的用户单选或多选关系字段解析办理人。
 */
@Component("entityUserReferenceFieldPersonResolver")
public class EntityUserReferenceFieldPersonResolver
        implements PersonResolver, PersonResolverConfigurationValidator {

    private static final PersonResolverDescriptor DESCRIPTOR =
            new PersonResolverDescriptor(
                    EntityUserReferenceFieldConfig.RESOLVER_CODE,
                    "实体用户关系字段",
                    "从流程绑定实体的已发布用户单选或多选关系字段读取人员。",
                    1,
                    1,
                    Set.of(
                            PersonResolveUsage.ASSIGNEE,
                            PersonResolveUsage.CANDIDATE,
                            PersonResolveUsage.MULTI_INSTANCE),
                    Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of(
                                    "schemaVersion",
                                    "entityCode",
                                    "fieldCode"),
                            "properties", Map.of(
                                    "schemaVersion", Map.of("const", 1),
                                    "entityCode", Map.of(
                                            "type", "string",
                                            "minLength", 1,
                                            "maxLength", 128),
                                    "fieldCode", Map.of(
                                            "type", "string",
                                            "minLength", 1,
                                            "maxLength", 100,
                                            "pattern",
                                            "^[A-Za-z][A-Za-z0-9_]{0,99}$"))),
                    false);

    private final EntityUserReferencePort referencePort;
    private final EntityCodeCatalogPort entityCodeCatalogPort;

    public EntityUserReferenceFieldPersonResolver(
            EntityUserReferencePort referencePort,
            EntityCodeCatalogPort entityCodeCatalogPort) {
        this.referencePort = referencePort;
        this.entityCodeCatalogPort = entityCodeCatalogPort;
    }

    @Override
    public PersonResolverDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public String resolverCode() {
        return EntityUserReferenceFieldConfig.RESOLVER_CODE;
    }

    /** 发布时使用实体元数据校验字段，而不是相信设计器提交的字段类型。 */
    @Override
    public void validate(
            PersonResolverConfigurationValidationRequest request) {
        EntityUserReferenceFieldConfig config =
                EntityUserReferenceFieldConfig.parse(
                        request.extraParams());
        if (StringUtils.hasText(request.processConfigId())) {
            String boundEntityCode = entityCodeCatalogPort
                    .findEntityCodeByProcessDefinitionId(
                            request.processConfigId());
            if (!config.entityCode().equals(boundEntityCode)) {
                throw new IllegalArgumentException(
                        EntityUserReferenceFieldConfig.RESOLVER_CODE
                                + " 配置无效: 字段实体与流程绑定实体不一致");
            }
        }
        config.validateAssignmentMode(
                request.assignmentMode(), request.multiInstance());
        UserReferenceField field =
                referencePort.requireUserReferenceField(
                config.entityCode(), config.fieldCode());
        if (request.usage() == PersonResolveUsage.ASSIGNEE) {
            config.validateFieldCardinality(
                    request.assignmentMode(),
                    request.multiInstance(),
                    field.multiple());
        }
    }

    /**
     * 节点激活时重新读取实体记录，确保此前任一表单保存的最新字段值都能生效。
     */
    @Override
    public PersonResolveResult resolve(PersonResolveRequest request) {
        final EntityUserReferenceFieldConfig config;
        try {
            config = EntityUserReferenceFieldConfig.parse(
                    request.extraParams());
        } catch (IllegalArgumentException exception) {
            throw new PersonResolutionException(
                    "ENTITY_USER_REFERENCE_CONFIG_INVALID",
                    exception.getMessage(),
                    Map.of(),
                    exception);
        }
        if (!StringUtils.hasText(request.entityCode())
                || !config.entityCode().equals(request.entityCode())) {
            throw new PersonResolutionException(
                    "ENTITY_CONTEXT_MISMATCH",
                    "流程实体与审批人字段配置不一致",
                    Map.of(
                            "configuredEntityCode", config.entityCode(),
                            "runtimeEntityCode", String.valueOf(
                                    request.entityCode())));
        }
        if (!StringUtils.hasText(request.entityDataId())) {
            throw new PersonResolutionException(
                    "ENTITY_RECORD_MISSING",
                    "流程上下文缺少实体记录 ID");
        }
        try {
            return PersonResolveResult.users(referencePort.readUserKeys(
                    config.entityCode(),
                    request.entityDataId(),
                    config.fieldCode()));
        } catch (EntityUserReferenceException exception) {
            // 仅业务可处置的端口失败进入空办理人策略；数据库与编程异常保持
            // 原类型失败关闭，避免基础设施故障被误报为“没有审批人”。
            throw new PersonResolutionException(
                    exception.reasonCode(),
                    exception.getMessage(),
                    Map.of(
                            "entityCode", config.entityCode(),
                            "entityDataId", request.entityDataId(),
                            "fieldCode", config.fieldCode()),
                    exception);
        }
    }
}
