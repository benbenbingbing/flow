package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 在 Entity 边界内校验 Embed 固定根和已发布表单组件路径。
 *
 * <p>这里不转换字段或组件配置。iframe 随后调用与 Flow 页面相同的
 * {@code /api/entity-forms/{formId}/runtime-release}，因此新增内建组件不需要修改
 * Embed DTO 或渲染白名单。</p>
 */
@Component
public class EntityEmbedNativeFormRuntimeAdapter
        implements EmbedNativeFormRuntimePort {

    private final EntityDefinitionMapper definitionMapper;
    private final UiConfigReleaseService releaseService;

    /**
     * 初始化实体嵌入式原生表单运行时适配器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param releaseService 发布版本服务依赖，保存到当前对象供后续业务方法调用
     */
    public EntityEmbedNativeFormRuntimeAdapter(
            EntityDefinitionMapper definitionMapper,
            UiConfigReleaseService releaseService) {
        this.definitionMapper = definitionMapper;
        this.releaseService = releaseService;
    }

    @Override
    public String configHash(String formId) { return releaseService.formConfigHash(formId); }

    @Override
    public VerifiedTarget authorizeTarget(String rootFormId, String formId, Map<String,Object> runtimeContext) {
        if (!StringUtils.hasText(rootFormId) || !StringUtils.hasText(formId)) throw new IllegalArgumentException("Embed 表单根不能为空");
        if (!Objects.equals(rootFormId, formId))
            releaseService.requireComponentPath(rootFormId, formId, runtimeContext == null ? null : runtimeContext.get("componentPath"));
        return currentFormTarget(null, formId);
    }

    /**
     * 精确解析固定 Form Release，并校验其实体归属。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单ID，后续用于校验并获取{@code owned}发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于校验并获取{@code owned}发布版本时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code resolveRuntimeFormRelease} 的输入影响后续处理
     * @return 校验并获取后的{@code owned}发布版本结果，供调用方继续处理
     */
    private EntityDefinition requireOwnedRelease(
            String entityCode,
            String formId,
            String releaseId,
            int releaseVersion) {
        ResolvedEntityFormRelease resolved = releaseService
                .resolveRuntimeFormRelease(
                        formId, releaseId, releaseVersion,
                        UiRuntimeResolutionContext.standalone());
        if (resolved == null
                || resolved.form() == null
                || !Objects.equals(formId, resolved.form().getId())
                || !Objects.equals(releaseId, resolved.releaseId())
                || !Objects.equals(
                        releaseVersion, resolved.releaseVersion())) {
            throw new IllegalArgumentException(
                    "Embed 原生表单发布坐标无效");
        }
        EntityDefinition definition;
        if (StringUtils.hasText(entityCode)) {
            definition = definitionMapper.findByEntityCode(entityCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Embed 原生表单实体不存在"));
        } else {
            definition = definitionMapper.selectById(
                    resolved.form().getEntityId());
            if (definition == null
                    || !StringUtils.hasText(
                            definition.getEntityCode())) {
                throw new IllegalArgumentException(
                        "Embed 原生表单实体不存在");
            }
        }
        if (!Objects.equals(
                definition.getId(), resolved.form().getEntityId())) {
            throw new IllegalArgumentException(
                    "Embed 原生表单发布坐标不属于固定实体");
        }
        return definition;
    }
    /** 读取当前配置并检查实体归属，供已有 Embed Session 重新加载当前表单。 */
    @Override
    public VerifiedTarget currentFormTarget(String entityCode, String formId) {
        ResolvedEntityFormRelease current = releaseService.resolveRuntimeFormRelease(formId);
        EntityDefinition entity = requireOwnedRelease(entityCode, formId, current.releaseId(), current.releaseVersion());
        return new VerifiedTarget(entity.getEntityCode(), formId, current.releaseId(), current.releaseVersion());
    }

}
