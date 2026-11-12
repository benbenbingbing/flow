package com.workflow.contracts.identity.position.port;

import com.workflow.contracts.identity.position.model.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.model.OrganizationBusinessLevelView;
import com.workflow.contracts.identity.position.error.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.position.model.OrganizationUnitStateView;
import com.workflow.contracts.identity.position.model.PositionDefinitionView;
import com.workflow.contracts.identity.position.model.PositionHolderResolution;
import java.util.List;

/**
 * 组织职务目录的跨模块查询端口。
 *
 * <p>流程模块只依赖本端口中的快照与查询投影，不得直接依赖系统管理
 * 模块的 Mapper、持久化记录或领域 Service。</p>
 */
public interface OrganizationPositionDirectoryPort {

    /**
     * 按本地用户 ID 或用户名捕获发起人当前的组织链快照。
     *
     * @param idOrUsername 已经完成外部身份映射的本地用户 ID 或用户名
     * @return 从部门/组织锚点到根节点的不可变快照
     * @throws OrganizationPositionDirectoryException 用户、组织归属或父链无法安全捕获时抛出
     */
    InitiatorOrganizationSnapshot captureInitiatorSnapshot(String idOrUsername);

    /**
     * 要求冻结链中的组织节点当前仍存在且启用。
     *
     * <p>解析器在跨越每个快照 ID 前调用该方法，防止越过已删除或停用的
     * 中间节点后继续路由。</p>
     *
     * @param organizationUnitId 组织单元ID，后续用于校验并获取活动组织单元时定位或关联目标
     * @return 校验并获取后的活动组织单元结果，供调用方继续处理
     */
    OrganizationUnitStateView requireActiveOrganizationUnit(
            String organizationUnitId);

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

    /**
     * 判断组织业务层级编码是否存在且可用，用于流程发布时静态校验。
     *
     * @param businessLevelCode 业务层级编码，后续用于判断是否组织业务层级启用时定位或关联目标
     * @return 组织业务层级启用条件成立时为 true，否则为 false
     */
    boolean isOrganizationBusinessLevelEnabled(String businessLevelCode);

    /**
     * 列出流程设计可选的已启用组织业务层级。
     *
     * @return 组织业务层级视图集合，供调用方遍历或展示
     */
    List<OrganizationBusinessLevelView> listEnabledOrganizationBusinessLevels();
}
