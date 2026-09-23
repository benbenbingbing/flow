package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.model.IdentityGroup;
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
