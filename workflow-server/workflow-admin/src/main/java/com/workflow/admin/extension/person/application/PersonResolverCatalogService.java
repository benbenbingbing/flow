package com.workflow.admin.extension.person.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.extension.person.api.request.PersonResolverDefinitionRequest;
import com.workflow.admin.extension.person.api.response.PersonResolverOption;
import com.workflow.admin.extension.person.infrastructure.persistence.mapper.PersonResolverDefinitionMapper;
import com.workflow.admin.extension.person.infrastructure.persistence.record.PersonResolverDefinition;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.process.assignment.spi.PersonResolver;
import com.workflow.contracts.process.assignment.model.PersonResolverDescriptor;
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

    /**
     * 列出可见；查询结果供调用方展示或继续处理。
     *
     * @param usage 使用场景，作为 {@code normalize} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code normalize} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 人员解析器选项集合，供调用方遍历或展示
     */
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

    /**
     * 列出全部{@code admin}；查询结果供调用方展示或继续处理。
     *
     * @return 人员解析器选项集合，供调用方遍历或展示
     */
    public List<PersonResolverOption> listAllForAdmin() {
        currentUserRoleService.requireAdministrator(
                "只有管理员可以查看人员接口目录");
        return buildOptions(true);
    }

    /**
     * 供统一扩展目录聚合，权限由聚合入口校验。
     *
     * @return 人员解析器选项集合，供调用方遍历或展示
     */
    public List<PersonResolverOption> listCatalog() {
        return buildOptions(true);
    }

    /**
     * 保存人员解析器目录；后续读取或执行将使用更新后的状态。
     *
     * @param resolverCode 解析器编码，后续用于保存人员解析器目录时定位或关联目标
     * @param request 本次请求，后续经校验后用于保存人员解析器目录
     * @return 保存后的人员解析器目录结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 构建选项；结果供后续流程传递或持久化。
     *
     * @param includeUnconfigured {@code include}{@code unconfigured}，供本方法构建选项时使用
     * @return 人员解析器选项集合，供调用方遍历或展示
     */
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

    /**
     * 整理解析器{@code beans}数据，供调用方遍历或继续处理。
     *
     * @return 解析器{@code beans}键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 转换为选项；输出作为后续校验或处理的输入。
     *
     * @param definition 定义，作为 {@code option.setDefinitionId} 的输入影响后续处理
     * @param resolverBean 解析器{@code bean}，作为 {@code option.setBeanName} 的输入影响后续处理
     * @return 转换为后的选项结果，供调用方继续处理
     */
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
        ExtensionImplementationOrigin implementationOrigin =
                resolverBean == null
                        ? null
                        : resolverBean.resolver().implementationOrigin();
        option.setImplementationOrigin(implementationOrigin == null
                ? ExtensionImplementationOrigin.UNKNOWN.name()
                : implementationOrigin.name());
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

    /**
     * resolver_code/deleted 唯一约束保证单条读取，无需再附加分页语法。
     *
     * @param resolverCode 解析器编码，后续用于查询编码时定位或关联目标
     * @return 符合条件的人员解析器定义结果，供调用方继续处理
     */
    private PersonResolverDefinition findByCode(String resolverCode) {
        return mapper.selectOne(
                new LambdaQueryWrapper<PersonResolverDefinition>()
                        .eq(PersonResolverDefinition::getResolverCode, resolverCode)
                        .eq(PersonResolverDefinition::getDeleted, 0));
    }

    /**
     * 判断是否匹配关键字；判断结果决定调用方的后续分支。
     *
     * @param item 条目，作为 {@code normalize} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code contains} 的输入影响后续处理
     * @return 关键字条件成立时为 true，否则为 false
     */
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

    /**
     * 写入人员解析器目录；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入人员解析器目录的原始输入，结果供调用方继续使用
     * @return 写入后的人员解析器目录文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "人员接口参数定义无法序列化", exception);
        }
    }

    /**
     * 读取字符串设置；查询结果供调用方展示或继续处理。
     *
     * @param value 待读取字符串设置的原始输入，结果供调用方继续使用
     * @return 人员解析器目录集合，供调用方遍历或展示
     */
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

    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param value 待读取映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
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

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化人员解析器目录的原始输入，结果供调用方继续使用
     * @return 规范化后的人员解析器目录文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    /**
     * 封装解析器{@code bean}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param beanName {@code bean}名称，后续用于处理解析器{@code bean}时匹配或展示
     * @param resolver 解析器，保存在对象中供后续校验、查询或展示
     */
    private record ResolverBean(String beanName, PersonResolver resolver) {
    }
}
