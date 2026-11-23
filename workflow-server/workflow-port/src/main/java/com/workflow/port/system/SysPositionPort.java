package com.workflow.port.system;

import com.workflow.port.system.identity.position.error.OrganizationPositionDirectoryException;
import com.workflow.port.system.identity.position.model.PositionDefinitionView;
import com.workflow.port.system.identity.position.model.PositionHolderResolution;
import java.util.List;

/** 读取启用的岗位定义及计算后的有效任职人。 */
public interface SysPositionPort {
    /**
     * 获取已启用的职务定义。
     *
     * @param positionCode 位置编码，后续用于校验并获取启用位置时定位或关联目标
     * @return 校验并获取后的启用位置结果，供调用方继续处理
     * @throws OrganizationPositionDirectoryException 职务不存在或已停用时抛出
     */
    PositionDefinitionView requireEnabledPosition(String positionCode);

    /**
     * 按可任职单位类型列出已启用职务；空类型表示不过滤。
     *
     * @param applicableUnitType 适用单元类型标识，决定后续启用{@code positions}采用的处理分支
     * @return 位置定义视图集合，供调用方遍历或展示
     */
    List<PositionDefinitionView> listEnabledPositions(String applicableUnitType);

    /**
     * 查询组织节点当前职务人员，过滤停用用户、职务和组织，按 userId 稳定排序。
     * 仅查询当前关系，已创建任务的办理人由流程自身保存。
     */
    PositionHolderResolution findCurrentHolders(String positionCode, String organizationUnitId);
}
