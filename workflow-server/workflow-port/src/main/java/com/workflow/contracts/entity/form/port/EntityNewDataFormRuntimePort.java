package com.workflow.contracts.entity.form.port;

import java.util.Optional;

/**
 * 复用 Flow 原生“新增数据表单”解析语义的稳定只读能力。
 *
 * <p>调用方可将解析得到的精确发布坐标固定到运行会话，而无需复制默认表单选择规则。</p>
 */
public interface EntityNewDataFormRuntimePort {

    /**
     * 按原生 Flow 规则解析当前新增表单发布版本。
     *
     * @param entityCode 实体编码
     * @return 已发布表单坐标；原生页面无可用表单时为空
     */
    Optional<ResolvedForm> resolveForNewData(String entityCode);

    /**
     * 原生解析得到的精确表单发布坐标。
     *
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     */
    record ResolvedForm(
            String formId,
            String releaseId,
            Integer releaseVersion) {
    }
}
