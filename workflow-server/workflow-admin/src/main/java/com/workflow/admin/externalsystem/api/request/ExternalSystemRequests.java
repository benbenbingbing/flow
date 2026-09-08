package com.workflow.admin.externalsystem.api.request;

import java.util.List;

/**
 * 外部系统及其参数集合的写接口请求契约。
 */
public final class ExternalSystemRequests {

    private ExternalSystemRequests() {
    }

    /**
     * 创建外部系统请求；系统编码创建后不可修改或复用。
     */
    public record CreateExternalSystem(
            String systemCode,
            String systemName,
            String status,
            String address,
            String description,
            List<ParameterInput> parameters) {
    }

    /**
     * 更新外部系统请求；契约刻意不包含 systemCode，防止修改稳定业务标识。
     * expectedVersion 必须来自最近一次列表或详情响应。
     */
    public record UpdateExternalSystem(
            String systemName,
            String status,
            String address,
            String description,
            List<ParameterInput> parameters,
            Long expectedVersion) {
    }

    /**
     * 启用或禁用外部系统请求，expectedVersion 用于检测并发修改。
     */
    public record ChangeExternalSystemStatus(
            String status,
            Long expectedVersion) {
    }

    /**
     * 删除外部系统请求；期望版本用于阻止旧页面删除已被他人修改的数据。
     */
    public record DeleteExternalSystem(Long expectedVersion) {
    }

    /**
     * 参数输入。id 仅用于兼容详情回填；聚合保存会重建参数行并返回新 id。
     */
    public record ParameterInput(
            String id,
            String nameZh,
            String nameEn,
            String value,
            Integer sortOrder) {
    }
}
