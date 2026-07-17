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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysGroupService {
    
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper userGroupMapper;
    
    /**
     * 查询组列表
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
     */
    public SysGroup getById(String id) {
        SysGroup group = groupMapper.selectById(id);
        if (group != null) {
            fillGroupUsers(group);
        }
        return group;
    }
    
    /**
     * 保存组
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
     * 删除组
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
     * 保存组用户关联
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
     * 填充组用户信息
     */
    private void fillGroupUsers(SysGroup group) {
        List<SysUser> users = groupMapper.selectGroupUsers(group.getId());
        group.setUsers(users);
        group.setUserIds(users.stream().map(SysUser::getId).collect(Collectors.toList()));
    }
}
