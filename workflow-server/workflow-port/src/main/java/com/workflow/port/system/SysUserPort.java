package com.workflow.port.system;

import com.workflow.port.system.model.identity.*;
import com.workflow.port.system.model.identity.IdentityDirectoryModels.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 用户资料查询与来源同步，目录、来源映射及扩展查询由调用方授权，来源写入沿用来源配置。 */
public interface SysUserPort {
    /** 根据用户筛选条件查询目录页。 */
    UserPage pageUsers(UserQuery query);

    /** 通过准确 ID 批量取目录详情，保持输入顺序去重，最多 100 个。 */
    List<IdentityUser> findUsersByIds(List<String> userIds);

    /** 通过准确用户名批量取目录详情，保持输入顺序去重，最多 100 个。 */
    List<IdentityUser> findUsersByUsernames(List<String> usernames);

    /** 按来源外部 ID 解析本地用户 ID。未绑定、已退休或已删除的映射为空。 */
    default Optional<String> findUserIdBySource(String sourceCode, String externalId) {
        return Optional.ofNullable(findUserIdsBySource(sourceCode, java.util.Collections.singletonList(externalId)).get(externalId));
    }

    /** 同一授权来源最多查 100 个外部 ID，按输入顺序去重，无映射的 ID 跳过。 */
    Map<String, String> findUserIdsBySource(String sourceCode, List<String> externalIds);

    /**
     * 保存用户快照及携带的扩展。来源键未命中时，按稳定账号自动定位可补绑或改绑的外部预配账号。
     * 映射、审计与资料一起提交；权限、编码或版本校验失败时整次回滚。
     */
    IdentitySaveResult saveUser(UserSaveModel model);

    /** 同来源最多 100 个用户，整批原子提交；已有同库事务须为 READ_COMMITTED，由外层决定最终提交。 */
    List<IdentitySaveResult> saveUsers(List<UserSaveModel> models);

    /** 未建档时为空；未知类型、未授权或对象不可见时抛边界异常。 */
    <T extends UserExtension> Optional<T> findUserExtension(String userId, Class<T> extensionType);
}
