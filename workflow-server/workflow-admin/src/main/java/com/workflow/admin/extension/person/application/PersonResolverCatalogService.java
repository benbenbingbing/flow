package com.workflow.admin.extension.person.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.extension.person.api.request.PersonResolverDefinitionRequest;
import com.workflow.admin.extension.person.api.response.PersonResolverOption;
import com.workflow.admin.extension.person.infrastructure.persistence.mapper.PersonResolverDefinitionMapper;
import com.workflow.admin.extension.person.infrastructure.persistence.record.PersonResolverDefinition;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 扫描并维护统一人员解析器目录。
 */
@Service
@RequiredArgsConstructor
public class PersonResolverCatalogService {

    private final PersonResolverDefinitionMapper mapper;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final CurrentUserRoleService currentUserRoleService;

    public List<PersonResolverOption> listVisible(
            String usage,
            String keyword,
            Integer limit) {
        String normalizedUsage = normalize(usage);
        String normalizedKeyword = normalize(keyword);
        int max = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return buildOptions(false).stream()
                .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> !StringUtils.hasText(normalizedUsage)
                        || item.getSupportedUsages().isEmpty()
                        || item.getSupportedUsages().contains(normalizedUsage))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .limit(max)
                .toList();
    }

    public List<PersonResolverOption> listAllForAdmin() {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以查看人员接口目录");
        return buildOptions(true);
    }

    /**
     * 供统一扩展目录聚合，权限由聚合入口校验。
     */
    public List<PersonResolverOption> listCatalog() {
        return buildOptions(true);
    }

    @Transactional
    public PersonResolverOption save(
            String resolverCode,
            PersonResolverDefinitionRequest request) {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以维护人员接口目录");
        ResolverBean resolverBean = resolverBeans().get(resolverCode);
        if (resolverBean == null) {
            throw new IllegalArgumentException(
                    "未找到人员解析器实现: " + resolverCode);
        }
        PersonResolverDescriptor descriptor = resolverBean.resolver().descriptor();
        PersonResolverDefinition definition = findByCode(resolverCode);
        boolean created = definition == null;
        if (created) {
            definition = new PersonResolverDefinition();
            definition.setResolverCode(descriptor.code());
            definition.setBeanName(resolverBean.beanName());
            definition.setCreatedAt(LocalDateTime.now());
            definition.setDeleted(0);
            definition.setRevision(1);
        } else {
            definition.setRevision(
                    definition.getRevision() == null
                            ? 1
                            : definition.getRevision() + 1);
        }
        definition.setDisplayName(request.getDisplayName().trim());
        definition.setDescription(trimToNull(request.getDescription()));
        definition.setBeanName(resolverBean.beanName());
        definition.setImplementationVersion(descriptor.implementationVersion());
        definition.setContractVersion(descriptor.contractVersion());
        definition.setSupportedUsagesDocument(write(
                descriptor.supportedUsages().stream()
                        .map(Enum::name)
                        .toList()));
        definition.setExtraParamSchemaDocument(write(descriptor.extraParamSchema()));
        definition.setDynamicExtraParams(descriptor.dynamicExtraParams());
        definition.setEnabled(request.getEnabled() == null || request.getEnabled());
        definition.setUpdatedAt(LocalDateTime.now());
        if (created) {
            mapper.insert(definition);
        } else {
            mapper.updateById(definition);
        }
        return toOption(definition, resolverBean);
    }

    private List<PersonResolverOption> buildOptions(
            boolean includeUnconfigured) {
        Map<String, ResolverBean> beans = resolverBeans();
        Map<String, PersonResolverDefinition> definitions =
                new LinkedHashMap<>();
        mapper.selectList(new LambdaQueryWrapper<PersonResolverDefinition>()
                        .eq(PersonResolverDefinition::getDeleted, 0)
                        .orderByAsc(PersonResolverDefinition::getResolverCode))
                .forEach(item -> definitions.put(item.getResolverCode(), item));

        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(definitions.keySet());
        codes.addAll(beans.keySet());
        List<PersonResolverOption> result = new ArrayList<>();
        for (String code : codes) {
            PersonResolverDefinition definition = definitions.get(code);
            if (!includeUnconfigured && definition == null) {
                continue;
            }
            result.add(toOption(definition, beans.get(code)));
        }
        return result;
    }

    private Map<String, ResolverBean> resolverBeans() {
        Map<String, ResolverBean> result = new LinkedHashMap<>();
        applicationContext.getBeansOfType(PersonResolver.class)
                .forEach((beanName, resolver) -> {
                    String code = resolver.descriptor().code();
                    ResolverBean previous = result.putIfAbsent(
                            code, new ResolverBean(beanName, resolver));
                    if (previous != null) {
                        throw new IllegalStateException(
                                "人员解析器编码重复: " + code);
                    }
                });
        return result;
    }

    private PersonResolverOption toOption(
            PersonResolverDefinition definition,
            ResolverBean resolverBean) {
        PersonResolverDescriptor descriptor = resolverBean == null
                ? null
                : resolverBean.resolver().descriptor();
        PersonResolverOption option = new PersonResolverOption();
        option.setDefinitionId(definition == null ? null : definition.getId());
        option.setResolverCode(definition != null
                ? definition.getResolverCode()
                : descriptor.code());
        option.setBeanName(resolverBean != null
                ? resolverBean.beanName()
                : definition == null ? null : definition.getBeanName());
        option.setClassName(resolverBean == null
                ? null
                : resolverBean.resolver().getClass().getName());
        option.setDisplayName(definition != null
                ? definition.getDisplayName()
                : descriptor.displayName());
        option.setDescription(definition != null
                ? definition.getDescription()
                : descriptor.description());
        option.setImplementationVersion(descriptor != null
                ? descriptor.implementationVersion()
                : definition.getImplementationVersion());
        option.setContractVersion(descriptor != null
                ? descriptor.contractVersion()
                : definition.getContractVersion());
        option.setSupportedUsages(descriptor != null
                ? descriptor.supportedUsages().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new))
                : readStringSet(definition.getSupportedUsagesDocument()));
        option.setExtraParamSchema(descriptor != null
                ? descriptor.extraParamSchema()
                : readMap(definition.getExtraParamSchemaDocument()));
        option.setDynamicExtraParams(descriptor != null
                ? descriptor.dynamicExtraParams()
                : Boolean.TRUE.equals(definition.getDynamicExtraParams()));
        option.setConfigured(definition != null);
        option.setAvailable(resolverBean != null);
        option.setEnabled(definition != null
                && Boolean.TRUE.equals(definition.getEnabled()));
        option.setRevision(definition == null ? null : definition.getRevision());
        return option;
    }

    private PersonResolverDefinition findByCode(String resolverCode) {
        return mapper.selectOne(
                new LambdaQueryWrapper<PersonResolverDefinition>()
                        .eq(PersonResolverDefinition::getResolverCode, resolverCode)
                        .eq(PersonResolverDefinition::getDeleted, 0)
                        .last("LIMIT 1"));
    }

    private boolean matchesKeyword(
            PersonResolverOption item,
            String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return normalize(item.getResolverCode()).contains(keyword)
                || normalize(item.getDisplayName()).contains(keyword)
                || normalize(item.getDescription()).contains(keyword);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "人员接口参数定义无法序列化", exception);
        }
    }

    private Set<String> readStringSet(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(
                    objectMapper.readValue(value, new TypeReference<List<String>>() {}));
        } catch (Exception exception) {
            return Set.of();
        }
    }

    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private record ResolverBean(String beanName, PersonResolver resolver) {
    }
}
