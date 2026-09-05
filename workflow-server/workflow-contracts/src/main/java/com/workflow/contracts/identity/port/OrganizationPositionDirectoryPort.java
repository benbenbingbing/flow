package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.contracts.identity.position.OrganizationBusinessLevelView;
import com.workflow.contracts.identity.position.OrganizationPositionDirectoryException;
import com.workflow.contracts.identity.position.OrganizationUnitStateView;
import com.workflow.contracts.identity.position.PositionDefinitionView;
import com.workflow.contracts.identity.position.PositionHolderResolution;
import java.time.Instant;
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
     */
    OrganizationUnitStateView requireActiveOrganizationUnit(
            String organizationUnitId);

    /**
     * 获取已启用的职务定义。
     *
     * @throws OrganizationPositionDirectoryException 职务不存在或已停用时抛出
     */
    PositionDefinitionView requireEnabledPosition(String positionCode);

    /** 按可任职单位类型列出已启用职务；空类型表示不过滤。 */
    List<PositionDefinitionView> listEnabledPositions(String applicableUnitType);

    /**
     * 查询某个组织节点在指定时刻的有效任职人。
     *
     * <p>适配器必须同时过滤停用用户、失效/撤销任职以及停用单位，并保持
     * {@code isPrimary DESC, sortOrder ASC, effectiveFrom ASC, userId ASC}
     * 的稳定顺序。</p>
     */
    PositionHolderResolution findEffectiveHolders(
            String positionCode,
            String organizationUnitId,
            Instant asOf);

    /** 判断组织业务层级编码是否存在且可用，用于流程发布时静态校验。 */
    boolean isOrganizationBusinessLevelEnabled(String businessLevelCode);

    /** 列出流程设计可选的已启用组织业务层级。 */
    List<OrganizationBusinessLevelView> listEnabledOrganizationBusinessLevels();
}
