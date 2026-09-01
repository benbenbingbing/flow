package com.workflow.entity.ui.infrastructure.adapter;

import com.workflow.contracts.embed.EmbedNativeTraversalRuntimePort;
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

    private static BusinessForbiddenException forbidden(String message) {
        return new BusinessForbiddenException(
                "INVALID_VIEW_COMPOSITION_TOKEN", message);
    }
}
