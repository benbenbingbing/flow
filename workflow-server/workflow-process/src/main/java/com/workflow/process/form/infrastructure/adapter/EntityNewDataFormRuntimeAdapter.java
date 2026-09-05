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

    /**
     * 复用 {@link EntityFormResolveService} 的默认表单与流程首节点回退规则，
     * 只裁剪出 Embed Launch 物化所需的不可变发布坐标。
     */
    @Override
    public Optional<ResolvedForm> resolveForNewData(String entityCode) {
        Map<String, Object> form =
                resolveService.resolveFormForNewData(entityCode);
        if (form == null) {
            return Optional.empty();
        }
        String formId = text(form.get("id"));
        String releaseId = text(form.get("runtimeReleaseId"));
        Integer releaseVersion = integer(form.get("runtimeReleaseVersion"));
        if (!StringUtils.hasText(formId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null
                || releaseVersion < 1) {
            throw new IllegalStateException(
                    "原生新增表单缺少精确发布坐标");
        }
        return Optional.of(new ResolvedForm(
                formId, releaseId, releaseVersion));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = text(value);
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
