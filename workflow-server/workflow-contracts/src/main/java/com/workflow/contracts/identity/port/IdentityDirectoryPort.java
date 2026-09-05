package com.workflow.contracts.identity.port;

import com.workflow.contracts.identity.IdentityGroup;
import com.workflow.contracts.identity.IdentityUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户目录查询端口。
 *
 * <p>业务模块不得直接依赖系统模块的用户 Service 或 Mapper。</p>
 */
public interface IdentityDirectoryPort {

    Optional<IdentityUser> findUser(String idOrUsername);

    Optional<IdentityGroup> findGroup(String idOrCode);

    List<IdentityUser> findGroupUsers(String idOrCode);

    String getDisplayName(String idOrUsername);

    String getDisplayNames(Collection<String> idsOrUsernames);
}
