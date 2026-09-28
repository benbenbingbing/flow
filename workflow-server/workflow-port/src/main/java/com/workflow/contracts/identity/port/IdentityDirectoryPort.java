package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.model.IdentityGroup;
import com.workflow.contracts.identity.model.IdentityHandoverUser;
import com.workflow.contracts.identity.model.IdentityUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户目录查询端口。
 *
 * <p>业务模块不得直接依赖系统模块的用户 Service 或 Mapper。</p>
 */
public interface IdentityDirectoryPort {

    /**
     * 查询用户；查询结果供调用方展示或继续处理。
     *
     * @param idOrUsername ID或用户名，后续用于查询用户时匹配或展示
     * @return 匹配的用户；未找到时为空
     */
    Optional<IdentityUser> findUser(String idOrUsername);

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
     * 在交接事务内锁定用户行并读取最新状态，防止接收人校验与交接之间被并发停用或删除。
     * 调用方必须先建立事务，否则实现将拒绝调用；锁一直持有到交接事务结束。
     *
     * @param id 精确用户 ID
     * @return 最小身份快照；用户不存在时为空，调用方仍需校验状态与删除标志
     */
    Optional<IdentityHandoverUser> lockHandoverUser(String id);

    /**
     * 查询分组；查询结果供调用方展示或继续处理。
     *
     * @param idOrCode ID或编码，后续用于查询分组时定位或关联目标
     * @return 匹配的分组；未找到时为空
     */
    Optional<IdentityGroup> findGroup(String idOrCode);

    /**
     * 查询分组用户集合；查询结果供调用方展示或继续处理。
     *
     * @param idOrCode ID或编码，后续用于查询分组用户集合时定位或关联目标
     * @return 身份用户集合，供调用方遍历或展示
     */
    List<IdentityUser> findGroupUsers(String idOrCode);

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @param idOrUsername ID或用户名，后续用于读取展示名称时匹配或展示
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    String getDisplayName(String idOrUsername);

    /**
     * 读取展示名称集合；查询结果供调用方展示或继续处理。
     *
     * @param idsOrUsernames ID 集合或{@code usernames}，供本方法读取展示名称集合时使用
     * @return 读取后的展示名称集合文本，供调用方比较或展示
     */
    String getDisplayNames(Collection<String> idsOrUsernames);
}
