package com.workflow.port.system;

import com.workflow.port.system.model.identity.*;
import com.workflow.port.system.model.identity.IdentityDirectoryModels.*;
import com.workflow.port.system.error.identity.position.OrganizationPositionDirectoryException;
import com.workflow.port.system.model.identity.position.InitiatorOrganizationSnapshot;
import com.workflow.port.system.model.identity.position.OrganizationBusinessLevelView;
import com.workflow.port.system.model.identity.position.OrganizationUnitStateView;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 组织资料查询、来源同步和流程选人所需的组织状态。 */
public interface SysOrganizationPort {
    /** 查找已完成父链关联的组织或部门。 */
    Optional<Organization> findOrganization(String organizationId);

    /** 按来源外部 ID 解析本地组织 ID。未绑定、已退休或已删除的映射为空。 */
    default Optional<String> findOrganizationIdBySource(String sourceCode, String externalId) {
        return Optional.ofNullable(findOrganizationIdsBySource(sourceCode, java.util.Collections.singletonList(externalId)).get(externalId));
    }

    /** 同一授权来源最多查 100 个外部 ID，按输入顺序去重，无映射的 ID 跳过。 */
    Map<String, String> findOrganizationIdsBySource(String sourceCode, List<String> externalIds);

    /** 按父 ID 延迟展开组织树；空值时返回根层。 */
    List<Organization> listOrganizationChildren(String parentId, String type);

    /** 按目录的组织或部门类型读取树，未指定时读取有效组织树。 */
    List<OrganizationNode> listOrganizationTree(String type);

    /** 返回由根至目标有序的父链，包含目标节点。 */
    List<Organization> listOrganizationAncestors(String organizationId);

    /** 保存组织基础快照与扩展；同来源父级允许后到，父链未齐时隐藏于组织树，补齐后自动维护路径及下级。 */
    IdentitySaveResult saveOrganization(OrganizationSaveModel model);

    /** 同来源最多 100 个组织，允许父级不在本批或尚未到达；按输入顺序返回，非法父链及其他错误整批回滚。 */
    List<IdentitySaveResult> saveOrganizations(List<OrganizationSaveModel> models);

    /** 组织版本的精确 ID 查询，不接受客户端任意类名。 */
    <T extends OrganizationExtension> Optional<T> findOrganizationExtension(String organizationId, Class<T> extensionType);

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
