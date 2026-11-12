package com.workflow.admin.identity.position.api.request;

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
     * 保存组织内某职务的完整当前人员集合，空集合表示清空。
     * expectedUserIds 是编辑时读取的集合，用于阻止过期页面覆盖其他管理员的修改。
     */
    public record SetMembers(String positionCode, String organizationUnitId,
                             List<String> userIds, List<String> expectedUserIds) { }

    /** 移除当前关系，revision 用于检测并发修改，不代表任职历史版本。 */
    public record RemoveAssignment(Integer revision) { }

    /** 负责人快捷入口使用相同的集合替换及并发检查规则。 */
    public record ChangeOrganizationLeader(String userId, List<String> expectedUserIds) { }
}
