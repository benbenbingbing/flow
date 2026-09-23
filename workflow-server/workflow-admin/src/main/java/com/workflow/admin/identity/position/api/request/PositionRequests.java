package com.workflow.admin.identity.position.api.request;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 职务定义与任职写接口的请求契约。
 */
public final class PositionRequests {

    /**
     * 初始化位置{@code requests}，保存构造参数供后续方法使用。
     */
    private PositionRequests() {
    }

    /**
     * 封装创建位置的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param positionCode 位置编码，后续用于处理创建位置时定位或关联目标
     * @param positionName 位置名称，后续用于处理创建位置时匹配或展示
     * @param applicableUnitType 适用单元类型标识，决定后续创建位置采用的处理分支
     * @param holderMode 持有者模式标识，决定后续创建位置采用的处理分支
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @param description 描述，保存在对象中供后续校验、查询或展示
     */
    public record CreatePosition(
            String positionCode,
            String positionName,
            String applicableUnitType,
            String holderMode,
            Integer sortOrder,
            String description) {
    }

    /**
     * 封装更新位置的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param positionName 位置名称，后续用于处理更新位置时匹配或展示
     * @param applicableUnitType 适用单元类型标识，决定后续更新位置采用的处理分支
     * @param holderMode 持有者模式标识，决定后续更新位置采用的处理分支
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     */
    public record UpdatePosition(
            String positionName,
            String applicableUnitType,
            String holderMode,
            Integer sortOrder,
            String description,
            Integer revision) {
    }

    /**
     * 封装变更位置状态的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param status 状态标识，决定后续变更位置状态采用的处理分支
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     */
    public record ChangePositionStatus(
            String status,
            Integer revision) {
    }

    /**
     * 封装删除位置的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     */
    public record DeletePosition(Integer revision) {
    }

    /**
     * 封装分配批次的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param atomic {@code atomic}，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param items 条目，保存在对象中供后续校验、查询或展示
     */
    public record AssignmentBatch(
            Boolean atomic,
            String reason,
            List<AssignmentItem> items) {
    }

    /**
     * 封装分配条目的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param positionCode 位置编码，后续用于处理分配条目时定位或关联目标
     * @param organizationUnitId 组织单元ID，后续用于处理分配条目时定位或关联目标
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param effectiveFrom 有效起始，保存在对象中供后续校验、查询或展示
     * @param effectiveTo 有效截止，保存在对象中供后续校验、查询或展示
     * @param isPrimary 是否主要，保存在对象中供后续校验、查询或展示
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @param replaceExisting 替换已有，保存在对象中供后续校验、查询或展示
     */
    public record AssignmentItem(
            String positionCode,
            String organizationUnitId,
            String userId,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            Boolean isPrimary,
            Integer sortOrder,
            Boolean replaceExisting) {
    }

    /**
     * 封装撤销分配的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     */
    public record RevokeAssignment(
            String reason,
            Integer revision) {
    }

    /**
     * 封装更新分配时段的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param effectiveFrom 有效起始，保存在对象中供后续校验、查询或展示
     * @param effectiveTo 有效截止，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     */
    public record UpdateAssignmentPeriod(
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            String reason,
            Integer revision) {
    }

    /**
     * 封装变更组织{@code leader}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param effectiveFrom 有效起始，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    public record ChangeOrganizationLeader(
            String userId,
            OffsetDateTime effectiveFrom,
            String reason) {
    }
}
