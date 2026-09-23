package com.workflow.admin.identity.infrastructure.adapter;

import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.contracts.identity.model.IdentityGroup;
import com.workflow.contracts.identity.model.IdentityUser;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.application.SysUserService;
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

    /**
     * 查询用户；查询结果供调用方展示或继续处理。
     *
     * @param idOrUsername ID或用户名，后续用于查询用户时匹配或展示
     * @return 匹配的用户；未找到时为空
     */
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

    /**
     * 查询分组；查询结果供调用方展示或继续处理。
     *
     * @param idOrCode ID或编码，后续用于查询分组时定位或关联目标
     * @return 匹配的分组；未找到时为空
     */
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

    /**
     * 查询分组用户集合；查询结果供调用方展示或继续处理。
     *
     * @param idOrCode ID或编码，后续用于查询分组用户集合时定位或关联目标
     * @return 身份用户集合，供调用方遍历或展示
     */
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

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @param idOrUsername ID或用户名，后续用于读取展示名称时匹配或展示
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName(String idOrUsername) {
        return userService.getDisplayName(idOrUsername);
    }

    /**
     * 读取展示名称集合；查询结果供调用方展示或继续处理。
     *
     * @param idsOrUsernames ID 集合或{@code usernames}，供本方法读取展示名称集合时使用
     * @return 读取后的展示名称集合文本，供调用方比较或展示
     */
    @Override
    public String getDisplayNames(Collection<String> idsOrUsernames) {
        return idsOrUsernames == null
                ? ""
                : userService.getDisplayNames(idsOrUsernames.stream().toList());
    }

    /**
     * 转换为身份用户；输出作为后续校验或处理的输入。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 转换为后的身份用户结果，供调用方继续处理
     */
    private IdentityUser toIdentityUser(SysUser user) {
        return new IdentityUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getOrgId(),
                user.getDeptId());
    }
}
