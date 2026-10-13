package com.workflow.entity.ui.infrastructure.adapter;

import com.workflow.contracts.embed.runtime.port.EmbedNativeTraversalRuntimePort;
import com.workflow.entity.ui.application.UiViewCompositionRuntimeService;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将关联内容业务关联路径转换为 Embed 可消费的服务端固定目标。
 *
 * <p>目标实体从已发布目标资产反查，绝不采用浏览器携带的 entityCode。
 * FORM 与 LIST 共用这一入口，因此后续原生组件只需传递通用遍历上下文。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityEmbedNativeTraversalRuntimeAdapter
        implements EmbedNativeTraversalRuntimePort {

    private final UiViewCompositionRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    /**
     * 解析实体嵌入式原生遍历运行时；输出作为后续校验或处理的输入。
     *
     * @param context 业务入口和关联路径；每一跳重新校验
     * @return 解析后的实体嵌入式原生遍历运行时结果，供调用方继续处理
     */
    @Override
    public TraversalTarget resolve(java.util.Map<String,Object> context) {
        UiViewCompositionResolveRequest request = objectMapper.convertValue(context, UiViewCompositionResolveRequest.class);
        var resolved = runtimeService.resolve(request);
        // 根身份保留业务 ID，旧版本坐标不再由客户端参与授权。
        return new TraversalTarget(request.getOwnerType(), request.getOwnerId(), null, null, request.getRecordId(),
                resolved.getTargetContentType(), resolved.getTargetContentId(), resolved.getTargetReleaseId(),
                resolved.getTargetReleaseVersion(), resolved.getTargetRecordId(), resolved.getTargetEntityCode());
    }

}
