package com.workflow.process.form.infrastructure.adapter;

import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort;
import com.workflow.process.form.application.EntityFormResolveService;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将流程模块的原生新增表单解析结果暴露为稳定的跨模块坐标。
 */
@Component
@RequiredArgsConstructor
public class EntityNewDataFormRuntimeAdapter
        implements EntityNewDataFormRuntimePort {

    private final EntityFormResolveService resolveService;
    private final com.workflow.entity.ui.application.UiConfigReleaseService releaseService;

    /**
     * 复用 {@link EntityFormResolveService} 的默认表单与流程首节点回退规则，
     * 只裁剪出 Embed Launch 物化所需的不可变发布坐标。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 匹配的新数据；未找到时为空
     */
    @Override
    public Optional<ResolvedForm> resolveForNewData(String entityCode) {
        Map<String, Object> form =
                resolveService.resolveFormForNewData(entityCode);
        if (form == null) {
            return Optional.empty();
        }
        String formId = text(form.get("id"));
        if (!StringUtils.hasText(formId)) {
            throw new IllegalStateException(
                    "原生新增表单缺少精确发布坐标");
        }
        // 内部 Embed 绑定可以记录实际配置身份，公共表单响应不再承担传递发布坐标的职责。
        var resolved = releaseService.resolveRuntimeFormRelease(formId);
        return Optional.of(new ResolvedForm(formId, resolved.releaseId(), resolved.releaseVersion()));
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

}
