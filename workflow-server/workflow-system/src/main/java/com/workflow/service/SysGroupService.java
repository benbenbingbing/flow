package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.SysGroup;
import com.workflow.entity.SysUser;
import com.workflow.entity.SysUserGroup;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysUserGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户组管理服务
 * <p>
 * 负责用户组的增删改查、状态切换以及组与用户的关联维护。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysGroupService {
    
    /** 用户组 Mapper */
    private final SysGroupMapper groupMapper;
    /** 用户组关联 Mapper，维护组与用户的关联关系 */
    private final SysUserGroupMapper userGroupMapper;
    
    /**
     * 查询组列表
     *
     * @return 按排序升序的用户组列表，每组已填充成员用户信息
     */
    public List<SysGroup> getGroupList() {
        List<SysGroup> groups = groupMapper.selectList(
            new LambdaQueryWrapper<SysGroup>()
                .orderByAsc(SysGroup::getSort)
        );
        groups.forEach(this::fillGroupUsers);
        return groups;
    }
    
    /**
     * 查询所有启用的组
     *
     * @return 启用状态的用户组列表，按排序升序
     */
    public List<SysGroup> getEnabledGroups() {
        return groupMapper.selectList(
            new LambdaQueryWrapper<SysGroup>()
                .eq(SysGroup::getStatus, SysGroup.Status.ENABLED.getValue())
                .orderByAsc(SysGroup::getSort)
        );
    }
    
    /**
     * 根据ID查询组
     *
     * @param id 组ID
     * @return 用户组对象（已填充成员用户信息），不存在返回 null
     */
    public SysGroup getById(String id) {
        SysGroup group = groupMapper.selectById(id);
        if (group != null) {
            fillGroupUsers(group);
        }
        return group;
    }
    
    /**
     * 保存组（新增或更新），并同步组与用户的关联
     *
     * @param group 用户组对象，userIds 为关联的用户ID列表
     * @return 保存后的用户组对象
     * @throws RuntimeException 组编码已存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public SysGroup saveGroup(SysGroup group) {
        // 校验组编码唯一性
        if (StringUtils.hasText(group.getGroupCode())) {
            String excludeId = group.getId() != null ? group.getId() : "";
            if (groupMapper.existsGroupCode(group.getGroupCode(), excludeId)) {
                throw new RuntimeException("组编码已存在：" + group.getGroupCode());
            }
        }
        
        // 设置默认值
        if (!StringUtils.hasText(group.getStatus())) {
            group.setStatus(SysGroup.Status.ENABLED.getValue());
        }
        if (group.getSort() == null) {
            group.setSort(0);
        }
        
        group.setUpdateTime(LocalDateTime.now());
        
        if (!StringUtils.hasText(group.getId())) {
            // 新增
            group.setCreateTime(LocalDateTime.now());
            groupMapper.insert(group);
            log.info("新增用户组：{}", group.getGroupName());
        } else {
            // 更新
            groupMapper.updateById(group);
            log.info("更新用户组：{}", group.getGroupName());
        }
        
        // 保存用户关联
        if (group.getUserIds() != null) {
            saveGroupUsers(group.getId(), group.getUserIds());
        }
        
        return group;
    }
    
    /**
     * 删除组（先删除用户关联，再逻辑删除组）
     *
     * @param id 组ID
     * @throws RuntimeException 组不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(String id) {
        SysGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new RuntimeException("组不存在");
        }
        
        // 删除用户关联
        userGroupMapper.deleteByGroupId(id);
        
        // 逻辑删除组
        groupMapper.deleteById(id);
        log.info("删除用户组：{}", group.getGroupName());
    }
    
    /**
     * 更新组状态
     *
     * @param id     组ID
     * @param status 状态值：0-启用 1-禁用
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status) {
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setStatus(status);
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(group);
    }
    
    /**
     * 保存组用户关联（先删除原有关联，再批量插入新关联）
     *
     * @param groupId  组ID
     * @param userIds 用户ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveGroupUsers(String groupId, List<String> userIds) {
        // 删除原有用户
        userGroupMapper.deleteByGroupId(groupId);
        
        // 添加新用户
        if (userIds != null && !userIds.isEmpty()) {
            for (String userId : userIds) {
                SysUserGroup userGroup = new SysUserGroup();
                userGroup.setGroupId(groupId);
                userGroup.setUserId(userId);
                userGroup.setCreateTime(LocalDateTime.now());
                userGroupMapper.insert(userGroup);
            }
        }
    }
    
    /**
     * 填充组用户信息（查询组成员并回填 users、userIds 字段）
     *
     * @param group 待填充的用户组对象
     */
    private void fillGroupUsers(SysGroup group) {
        List<SysUser> users = groupMapper.selectGroupUsers(group.getId());
        group.setUsers(users);
        group.setUserIds(users.stream().map(SysUser::getId).collect(Collectors.toList()));
    }
}
