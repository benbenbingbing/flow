package com.workflow.port.system;

import com.workflow.port.system.error.identity.position.OrganizationPositionDirectoryException;
import com.workflow.port.system.model.identity.position.PositionDefinitionView;
import com.workflow.port.system.model.identity.position.PositionHolderResolution;
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
     * 列出可在组织和部门任职的全部启用职务。
     *
     * @return 位置定义视图集合，供调用方遍历或展示
     */
    List<PositionDefinitionView> listEnabledPositions();

    /**
     * 按名称或编码搜索启用职务，供流程设计器分页选取。
     *
     * @param keyword 可为空的搜索词
     * @param pageNumber 已规范化的页码，从 1 开始
     * @param pageSize 已规范化的页大小
     * @return 当前页轻量职务定义与总数
     */
    PositionPage pageEnabledPositions(String keyword, long pageNumber, int pageSize);

    /**
     * 职务选择页。总数用于计算符合当前搜索条件的可翻页范围。
     *
     * @param records 当前页定义
     * @param total 符合查询条件的总数
     */
    record PositionPage(List<PositionDefinitionView> records, long total) {}

    /**
     * 按配置中的稳定编码回显职务，缺失或停用的编码由设计器标记为不可用。
     *
     * @param codes 已保存的职务编码
     * @return 仍可选择的职务定义
     */
    List<PositionDefinitionView> findEnabledPositionsByCodes(List<String> codes);

    /**
     * 查询组织节点当前职务人员，过滤停用用户、职务和组织，按 userId 稳定排序。
     * 仅查询当前关系，已创建任务的办理人由流程自身保存。
     */
    PositionHolderResolution findCurrentHolders(String positionCode, String organizationUnitId);
}
