package com.workflow.admin.externalsystem.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 外部系统管理只读响应契约，不暴露逻辑删除等持久化控制字段。
 */
public final class ExternalSystemViews {

    private ExternalSystemViews() {
    }

    /**
     * 列表页使用的外部系统摘要，避免在分页响应中批量暴露参数值。
     */
    public record ExternalSystemSummary(
            String id,
            String systemCode,
            String systemName,
            String status,
            String address,
            String description,
            long version,
            int parameterCount,
            String createdBy,
            String updatedBy,
            Instant createTime,
            Instant updateTime) {
    }

    /**
     * 外部系统详情，包含当前活动参数集合。
     */
    public record ExternalSystemDetail(
            String id,
            String systemCode,
            String systemName,
            String status,
            String address,
            String description,
            long version,
            List<ExternalSystemParameterView> parameters,
            String createdBy,
            String updatedBy,
            Instant createTime,
            Instant updateTime) {

        /**
         * 为系统审计切面提供 JavaBean 风格目标 ID 访问器。
         *
         * @return 外部系统 ID
         */
        public String getId() {
            return id;
        }
    }

    /**
     * 外部系统参数详情。
     */
    public record ExternalSystemParameterView(
            String id,
            String nameZh,
            String nameEn,
            String value,
            int sortOrder) {
    }
}
