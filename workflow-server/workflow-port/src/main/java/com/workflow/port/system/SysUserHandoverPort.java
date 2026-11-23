package com.workflow.port.system;

import com.workflow.port.system.identity.model.IdentityHandoverUser;
import java.util.List;
import java.util.Optional;

/** 查询任务交接涉及的用户，支持历史账号和交接事务内锁定。 */
public interface SysUserHandoverPort {
    /**
     * 按精确 ID 查询交接身份，包括停用和逻辑删除用户，供管理员处理其遗留待办。
     *
     * @param id 用户 ID；不按用户名回退，避免交接到同名但不同 ID 的账号
     * @return 最小身份快照；空 ID 或用户不存在时为空
     */
    Optional<IdentityHandoverUser> findHandoverUser(String id);

    /**
     * 搜索交接来源或接收人，最多返回 200 人，按用户名、ID 稳定排序。
     *
     * @param keyword ID、用户名或昵称关键字；空值返回排序靠前的用户
     * @param targetOnly 为 true 时仅返回状态正常且未删除的接收人，否则不限制来源状态
     * @return 不包含密码、会话等敏感数据的身份集合
     */
    List<IdentityHandoverUser> searchHandoverUsers(String keyword, boolean targetOnly);

    /**
     * 带锁读取。在交接事务内锁定用户行并读取最新状态，防止接收人校验与交接之间被并发停用或删除。
     * 调用方必须先建立事务，否则实现将拒绝调用；锁一直持有到交接事务结束。
     *
     * @param id 精确用户 ID
     * @return 最小身份快照；用户不存在时为空，调用方仍需校验状态与删除标志
     */
    Optional<IdentityHandoverUser> lockHandoverUser(String id);
}
