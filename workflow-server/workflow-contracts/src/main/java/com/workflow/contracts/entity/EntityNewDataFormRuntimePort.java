package com.workflow.contracts.entity;

import java.util.Optional;

/**
 * 跨模块复用 Flow 原生“新增数据表单”解析语义的只读端口。
 *
 * <p>返回的是当次解析已确定的表单与精确发布坐标；调用方可以在
 * 运行 Session 创建时将其固定，不必复制“默认表单/流程首节点”的
 * 选择规则。</p>
 */
public interface EntityNewDataFormRuntimePort {

    /**
     * 按原生 Flow 规则解析当前新增表单发布版本。
     *
     * @param entityCode 实体编码
     * @return 已发布表单坐标；原生页面无可用表单时为空
     */
    Optional<ResolvedForm> resolveForNewData(String entityCode);

    /** 原生解析得到的精确表单发布坐标。 */
    record ResolvedForm(
            String formId,
            String releaseId,
            Integer releaseVersion) {
    }
}
