package com.workflow.entity.ui.infrastructure.adapter;

import com.workflow.contracts.embed.runtime.port.EmbedNativeTraversalRuntimePort;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiViewCompositionTokenService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将关联内容签名遍历链转换为 Embed 可消费的服务端固定目标。
 *
 * <p>目标实体从已发布目标资产反查，绝不采用浏览器携带的 entityCode。
 * FORM 与 LIST 共用这一入口，因此后续原生组件只需传递通用遍历上下文。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityEmbedNativeTraversalRuntimeAdapter
        implements EmbedNativeTraversalRuntimePort {

    private static final String FORM = "FORM";
    private static final String LIST = "LIST";

    private final UiViewCompositionTokenService tokenService;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityDefinitionMapper definitionMapper;

    /**
     * 解析实体嵌入式原生遍历运行时；输出作为后续校验或处理的输入。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 解析后的实体嵌入式原生遍历运行时结果，供调用方继续处理
     */
    @Override
    public TraversalTarget resolve(String token) {
        UiViewCompositionTokenService.Claims claims =
                tokenService.verifyTraversalContext(token);
        List<UiViewCompositionTokenService.TraversalHop> hops =
                claims.traversal();
        if (hops == null || hops.isEmpty()) {
            throw forbidden("关联内容遍历令牌缺少根宿主");
        }
        UiViewCompositionTokenService.TraversalHop root = hops.get(0);
        String entityCode = resolveEntityCode(
                claims.nextOwnerType(), claims.nextOwnerId());
        return new TraversalTarget(
                root.ownerType(), root.ownerId(), root.releaseId(),
                root.releaseVersion(), root.recordId(),
                claims.nextOwnerType(), claims.nextOwnerId(),
                claims.nextReleaseId(), claims.nextReleaseVersion(),
                claims.nextRecordId(), entityCode);
    }

    /**
     * 解析实体编码；输出作为后续校验或处理的输入。
     *
     * @param ownerType 归属方类型标识，决定后续实体编码采用的处理分支
     * @param ownerId 归属方ID，后续用于解析实体编码时定位或关联目标
     * @return 解析后的实体编码文本，供调用方比较或展示
     */
    private String resolveEntityCode(String ownerType, String ownerId) {
        if (FORM.equals(ownerType)) {
            EntityForm form = formMapper.selectById(ownerId);
            EntityDefinition entity = form == null
                    ? null : definitionMapper.selectById(form.getEntityId());
            if (entity != null && StringUtils.hasText(entity.getEntityCode())) {
                return entity.getEntityCode();
            }
        } else if (LIST.equals(ownerType)) {
            EntityListConfig list = listMapper.selectById(ownerId);
            if (list != null && StringUtils.hasText(list.getEntityCode())) {
                return list.getEntityCode();
            }
        }
        throw forbidden("关联内容遍历目标不存在或类型不受支持");
    }

    /**
     * 构造权限不足异常，供调用方停止当前操作。
     *
     * @param message 消息，作为 {@code BusinessForbiddenException} 的输入影响后续处理
     * @return 处理后的禁止结果，供调用方继续处理
     */
    private static BusinessForbiddenException forbidden(String message) {
        return new BusinessForbiddenException(
                "INVALID_VIEW_COMPOSITION_TOKEN", message);
    }
}
