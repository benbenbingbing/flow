package com.workflow.admin.identity.position.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 职务定义、组织任职及批量预检的只读响应契约。
 */
public final class PositionViews {

    /**
     * 初始化位置视图，保存构造参数供后续方法使用。
     */
    private PositionViews() {
    }

    /**
     * 封装位置视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param positionCode 位置编码，后续用于处理位置视图时定位或关联目标
     * @param positionName 位置名称，后续用于处理位置视图时匹配或展示
     * @param applicableUnitType 适用单元类型标识，决定后续位置视图采用的处理分支
     * @param holderMode 持有者模式标识，决定后续位置视图采用的处理分支
     * @param builtIn {@code built}，保存在对象中供后续校验、查询或展示
     * @param status 状态标识，决定后续位置视图采用的处理分支
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @param description 描述，保存在对象中供后续校验、查询或展示
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     * @param currentAssignmentCount 当前分配数量，保存在对象中供后续校验、查询或展示
     * @param processReferenceCount 流程引用数量，保存在对象中供后续校验、查询或展示
     * @param createdBy 已创建，保存在对象中供后续校验、查询或展示
     * @param updatedBy {@code updated}，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record PositionView(
            String id,
            String positionCode,
            String positionName,
            String applicableUnitType,
            String holderMode,
            boolean builtIn,
            String status,
            int sortOrder,
            String description,
            int revision,
            long currentAssignmentCount,
            long processReferenceCount,
            String createdBy,
            String updatedBy,
            Instant createTime,
            Instant updateTime) {
    }

    /**
     * 封装分配视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param positionId 位置ID，后续用于处理分配视图时定位或关联目标
     * @param positionCode 位置编码，后续用于处理分配视图时定位或关联目标
     * @param positionName 位置名称，后续用于处理分配视图时匹配或展示
     * @param organizationUnitId 组织单元ID，后续用于处理分配视图时定位或关联目标
     * @param organizationUnitName 组织单元名称，后续用于处理分配视图时匹配或展示
     * @param organizationUnitType 组织单元类型标识，决定后续分配视图采用的处理分支
     * @param businessLevelCode 业务层级编码，后续用于处理分配视图时定位或关联目标
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param displayName 用户可见名称，供界面和日志展示
     * @param isPrimary 是否主要，保存在对象中供后续校验、查询或展示
     * @param sortOrder 排序权重，后续用于稳定展示顺序
     * @param effectiveFrom 有效起始，保存在对象中供后续校验、查询或展示
     * @param effectiveTo 有效截止，保存在对象中供后续校验、查询或展示
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param revokedBy 已撤销，保存在对象中供后续校验、查询或展示
     * @param revokeReason 撤销原因，保存在对象中供后续校验、查询或展示
     * @param revision 修订版本，保存在对象中供后续校验、查询或展示
     * @param active 活动，保存在对象中供后续校验、查询或展示
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record AssignmentView(
            String id,
            String positionId,
            String positionCode,
            String positionName,
            String organizationUnitId,
            String organizationUnitName,
            String organizationUnitType,
            String businessLevelCode,
            String userId,
            String username,
            String displayName,
            boolean isPrimary,
            int sortOrder,
            Instant effectiveFrom,
            Instant effectiveTo,
            Instant revokedAt,
            String revokedBy,
            String revokeReason,
            int revision,
            boolean active,
            Instant createTime,
            Instant updateTime) {
    }

    /**
     * 封装组织分配{@code matrix}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param organizationUnitId 组织单元ID，后续用于处理组织分配{@code matrix}时定位或关联目标
     * @param organizationUnitName 组织单元名称，后续用于处理组织分配{@code matrix}时匹配或展示
     * @param organizationUnitType 组织单元类型标识，决定后续组织分配{@code matrix}采用的处理分支
     * @param businessLevelCode 业务层级编码，后续用于处理组织分配{@code matrix}时定位或关联目标
     * @param assignments 分配集合，保存在对象中供后续校验、查询或展示
     */
    public record OrganizationAssignmentMatrix(
            String organizationUnitId,
            String organizationUnitName,
            String organizationUnitType,
            String businessLevelCode,
            List<AssignmentView> assignments) {
    }

    /**
     * 封装用户分配集合的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param displayName 用户可见名称，供界面和日志展示
     * @param assignments 分配集合，保存在对象中供后续校验、查询或展示
     */
    public record UserAssignments(
            String userId,
            String username,
            String displayName,
            List<AssignmentView> assignments) {
    }

    /**
     * 封装预检查的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param valid 有效，后续用于处理预检查结果时定位或关联目标
     * @param items 条目，保存在对象中供后续校验、查询或展示
     */
    public record PrecheckResult(
            boolean valid,
            List<PrecheckItem> items) {
    }

    /**
     * 封装预检查条目的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param index 索引，保存在对象中供后续校验、查询或展示
     * @param valid 有效，后续用于处理预检查条目时定位或关联目标
     * @param errorCode 错误编码，后续用于处理预检查条目时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public record PrecheckItem(
            int index,
            boolean valid,
            String errorCode,
            String message) {
    }

    /**
     * 封装批次分配的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param replayed {@code replayed}，保存在对象中供后续校验、查询或展示
     * @param assignmentIds 分配ID 集合，保存在对象中供后续校验、查询或展示
     */
    public record BatchAssignmentResult(
            String idempotencyKey,
            boolean replayed,
            List<String> assignmentIds) {
    }
}
