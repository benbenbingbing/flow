package com.workflow.admin.externalsystem.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 外部系统管理只读响应契约，不暴露逻辑删除等持久化控制字段。
 */
public final class ExternalSystemViews {

    /**
     * 初始化外部系统视图，保存构造参数供后续方法使用。
     */
    private ExternalSystemViews() {
    }

    /**
     * 列表页使用的外部系统摘要，避免在分页响应中批量暴露参数值。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param systemCode 系统编码，后续用于处理外部系统摘要时定位或关联目标
     * @param systemName 系统名称，后续用于处理外部系统摘要时匹配或展示
     * @param status 状态标识，决定后续外部系统摘要采用的处理分支
     * @param address 地址，保存在对象中供后续校验、查询或展示
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param parameterCount 参数数量，保存在对象中供后续校验、查询或展示
     * @param createdBy 已创建，保存在对象中供后续校验、查询或展示
     * @param updatedBy {@code updated}，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
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
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param systemCode 系统编码，后续用于处理外部系统详情时定位或关联目标
     * @param systemName 系统名称，后续用于处理外部系统详情时匹配或展示
     * @param status 状态标识，决定后续外部系统详情采用的处理分支
     * @param address 地址，保存在对象中供后续校验、查询或展示
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param version 版本，保存在对象中供后续校验、查询或展示
     * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
     * @param createdBy 已创建，保存在对象中供后续校验、查询或展示
     * @param updatedBy {@code updated}，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
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
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param nameZh 名称{@code zh}，保存在对象中供后续校验、查询或展示
     * @param nameEn 名称{@code en}，保存在对象中供后续校验、查询或展示
     * @param value 待处理外部系统参数视图的原始输入，结果供调用方继续使用
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     */
    public record ExternalSystemParameterView(
            String id,
            String nameZh,
            String nameEn,
            String value,
            int sortOrder) {
    }
}
