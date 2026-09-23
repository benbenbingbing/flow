package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 在 Entity 边界内校验 Embed 固定目标并签发原生表单发布解析令牌。
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
    private final UiReleaseResolutionTokenService resolutionTokenService;

    /**
     * 初始化实体嵌入式原生表单运行时适配器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param releaseService 发布版本服务依赖，保存到当前对象供后续业务方法调用
     * @param resolutionTokenService 解析令牌服务依赖，保存到当前对象供后续业务方法调用
     */
    public EntityEmbedNativeFormRuntimeAdapter(
            EntityDefinitionMapper definitionMapper,
            UiConfigReleaseService releaseService,
            UiReleaseResolutionTokenService resolutionTokenService) {
        this.definitionMapper = definitionMapper;
        this.releaseService = releaseService;
        this.resolutionTokenService = resolutionTokenService;
    }

    /**
     * 校验固定发布版本属于固定实体后签发用户与 Embed Session 双重绑定的上下文。
     *
     * @param target 目标，作为 {@code hasText} 的输入影响后续处理
     * @return 处理后的签发发布版本解析令牌文本，供调用方比较或展示
     */
    @Override
    public String issueReleaseResolutionToken(Target target) {
        if (target == null
                || !StringUtils.hasText(target.entityCode())
                || !StringUtils.hasText(target.formId())
                || !StringUtils.hasText(target.formReleaseId())
                || target.formReleaseVersion() < 1
                || target.sessionAbsoluteExpiresAt() == null) {
            throw new IllegalArgumentException("Embed 原生表单目标不完整");
        }
        requireOwnedRelease(
                target.entityCode(), target.formId(),
                target.formReleaseId(), target.formReleaseVersion());
        String token = resolutionTokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                target.formId(),
                target.formReleaseId(),
                target.formReleaseVersion(),
                0,
                target.sessionAbsoluteExpiresAt());
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Embed 原生表单解析令牌签发失败");
        }
        return token;
    }

    /**
     * 验证服务端签名 token、Embed Session 绑定与表单实体归属，返回可信坐标。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param target 目标，作为 {@code requireSessionBoundClaims} 的输入影响后续处理
     * @return 验证后的发布版本解析令牌结果，供调用方继续处理
     */
    @Override
    public VerifiedTarget verifyReleaseResolutionToken(
            String token,
            VerificationTarget target) {
        UiReleaseResolutionTokenService.Claims claims =
                requireSessionBoundClaims(token, target);
        if (!Objects.equals(target.formId(), claims.parentFormId())
                || !Objects.equals(
                        target.formReleaseId(), claims.parentReleaseId())
                || !Objects.equals(
                        target.formReleaseVersion(),
                        claims.parentReleaseVersion())) {
            throw new IllegalArgumentException(
                    "Embed 原生表单令牌未精确绑定请求目标");
        }
        return verifiedOwnedTarget(target);
    }

    /**
     * 表单快照首次加载允许携带父表单 token，但必须复用平台
     * {@code referencesChildRelease} 链验证父快照对目标子表单的引用。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param target 目标，作为 {@code requireSessionBoundClaims} 的输入影响后续处理
     * @return 验证后的运行时发布版本请求结果，供调用方继续处理
     */
    @Override
    public VerifiedTarget verifyRuntimeReleaseRequest(
            String token,
            VerificationTarget target) {
        requireSessionBoundClaims(token, target);
        Map<String, Object> resolved = releaseService.runtimeFormRelease(
                target.formId(), target.formReleaseId(),
                target.formReleaseVersion(), token);
        if (!Objects.equals(target.formId(), resolved.get("configId"))
                || !Objects.equals(
                        target.formReleaseId(), resolved.get("id"))
                || !Objects.equals(
                        target.formReleaseVersion(), resolved.get("version"))) {
            throw new IllegalArgumentException(
                    "Embed 原生表单令牌解析结果与请求坐标不一致");
        }
        return verifiedOwnedTarget(target);
    }

    /**
     * 处理已验证{@code owned}目标，并将结果传给后续步骤。
     *
     * @param target 目标，作为 {@code requireOwnedRelease} 的输入影响后续处理
     * @return 处理后的已验证{@code owned}目标结果，供调用方继续处理
     */
    private VerifiedTarget verifiedOwnedTarget(
            VerificationTarget target) {
        EntityDefinition definition = requireOwnedRelease(
                target.entityCode(), target.formId(),
                target.formReleaseId(), target.formReleaseVersion());
        return new VerifiedTarget(
                definition.getEntityCode(), target.formId(),
                target.formReleaseId(), target.formReleaseVersion());
    }

    /**
     * 验证 token 只在当前 Embed Session/View 内有效，且不能越过 Session 上限。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param target 目标，供本方法校验并获取会话绑定声明集合时使用
     * @return 校验并获取后的会话绑定声明集合结果，供调用方继续处理
     */
    private UiReleaseResolutionTokenService.Claims requireSessionBoundClaims(
            String token,
            VerificationTarget target) {
        if (target == null
                || !StringUtils.hasText(target.formId())
                || !StringUtils.hasText(target.formReleaseId())
                || target.formReleaseVersion() < 1
                || !StringUtils.hasText(target.sessionId())
                || !StringUtils.hasText(target.viewReleaseId())
                || target.sessionAbsoluteExpiresAt() == null) {
            throw new IllegalArgumentException("Embed 原生表单验证目标不完整");
        }
        UiReleaseResolutionTokenService.Claims claims =
                resolutionTokenService.verify(token);
        if (!Objects.equals(
                        target.sessionId(), claims.embedSessionId())
                || !Objects.equals(
                        target.viewReleaseId(), claims.embedViewReleaseId())
                || claims.expiresAt()
                        > target.sessionAbsoluteExpiresAt().getEpochSecond()) {
            throw new IllegalArgumentException(
                    "Embed 原生表单令牌与当前 Session 固定目标不一致");
        }
        return claims;
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
}
