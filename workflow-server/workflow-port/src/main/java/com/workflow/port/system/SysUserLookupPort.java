package com.workflow.port.system;

import com.workflow.port.system.identity.model.IdentityUser;
import java.util.Collection;
import java.util.Optional;

/** 供平台内部按用户键定位身份和展示名称。 */
public interface SysUserLookupPort {
    /**
     * 查询用户；查询结果供调用方展示或继续处理。
     *
     * @param idOrUsername ID或用户名，后续用于查询用户时匹配或展示
     * @return 匹配的用户；未找到时为空
     */
    Optional<IdentityUser> findUser(String idOrUsername);

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
