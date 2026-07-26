package com.workflow.system.identity;

import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.contracts.identity.IdentityGroup;
import com.workflow.contracts.identity.IdentityUser;
import com.workflow.entity.SysGroup;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 基于系统用户服务实现统一身份目录查询。
 */
@Component
@RequiredArgsConstructor
public class IdentityDirectoryAdapter implements IdentityDirectoryPort {

    private final SysUserService userService;
    private final SysGroupMapper groupMapper;

    @Override
    public Optional<IdentityUser> findUser(String idOrUsername) {
        if (!StringUtils.hasText(idOrUsername)) {
            return Optional.empty();
        }
        SysUser user = userService.getByUsername(idOrUsername);
        if (user == null) {
            user = userService.getById(idOrUsername);
        }
        return Optional.ofNullable(user).map(this::toIdentityUser);
    }

    @Override
    public Optional<IdentityGroup> findGroup(String idOrCode) {
        if (!StringUtils.hasText(idOrCode)) {
            return Optional.empty();
        }
        SysGroup group = groupMapper.selectByGroupCode(idOrCode);
        if (group == null) {
            group = groupMapper.selectById(idOrCode);
        }
        return Optional.ofNullable(group)
                .map(value -> new IdentityGroup(
                        value.getId(),
                        value.getGroupCode(),
                        value.getGroupName()));
    }

    @Override
    public List<IdentityUser> findGroupUsers(String idOrCode) {
        return findGroup(idOrCode)
                .map(group -> {
                    List<SysUser> users = groupMapper.selectGroupUsers(group.id());
                    return users == null
                            ? List.<IdentityUser>of()
                            : users.stream().map(this::toIdentityUser).toList();
                })
                .orElseGet(List::of);
    }

    @Override
    public String getDisplayName(String idOrUsername) {
        return userService.getDisplayName(idOrUsername);
    }

    @Override
    public String getDisplayNames(Collection<String> idsOrUsernames) {
        return idsOrUsernames == null
                ? ""
                : userService.getDisplayNames(idsOrUsernames.stream().toList());
    }

    private IdentityUser toIdentityUser(SysUser user) {
        return new IdentityUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getOrgId(),
                user.getDeptId());
    }
}
