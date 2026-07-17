package com.workflow.service.impl;

import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.service.SysOrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 组织部门服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOrganizationServiceImpl implements SysOrganizationService {
    
    private final SysOrganizationMapper orgMapper;
    private final SysUserMapper userMapper;
    
    @Override
    public List<SysOrganization> getOrgTree(String type) {
        List<SysOrganization> allList;
        if (StringUtils.hasText(type)) {
            allList = orgMapper.selectByType(type);
        } else {
            allList = orgMapper.selectEnabledList();
        }
        return buildTree(allList);
    }
    
    @Override
    public List<SysOrganization> getEnabledList() {
        return orgMapper.selectEnabledList();
    }
    
    @Override
    public SysOrganization getById(String id) {
        SysOrganization org = orgMapper.selectById(id);
        if (org != null) {
            if (!"0".equals(org.getParentId())) {
                SysOrganization parent = orgMapper.selectById(org.getParentId());
                if (parent != null) {
                    org.setParentName(parent.getOrgName());
                }
            }
            org.setUserCount(orgMapper.countUsers(org.getId()));
        }
        return org;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysOrganization saveOrg(SysOrganization org) {
        if (StringUtils.hasText(org.getOrgCode())) {
            String excludeId = org.getId() != null ? org.getId() : "";
            if (orgMapper.existsCode(org.getOrgCode(), excludeId)) {
                throw new RuntimeException("组织编码已存在：" + org.getOrgCode());
            }
        }
        
        if (!StringUtils.hasText(org.getParentId())) {
            org.setParentId("0");
        }
        if (org.getSortOrder() == null) {
            org.setSortOrder(0);
        }
        if (!StringUtils.hasText(org.getStatus())) {
            org.setStatus(SysOrganization.Status.ENABLED.getValue());
        }
        
        calcLevelAndPath(org);
        org.setUpdateTime(LocalDateTime.now());
        
        if (!StringUtils.hasText(org.getId())) {
            org.setCreateTime(LocalDateTime.now());
            orgMapper.insert(org);
            log.info("新增组织部门：{} ({})", org.getOrgName(), org.getOrgCode());
        } else {
            SysOrganization oldOrg = orgMapper.selectById(org.getId());
            if (oldOrg != null && !oldOrg.getPath().equals(org.getPath())) {
                updateChildrenPath(oldOrg.getPath(), org.getPath());
            }
            orgMapper.updateById(org);
            log.info("更新组织部门：{} ({})", org.getOrgName(), org.getOrgCode());
        }
        
        return org;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrg(String id) {
        SysOrganization org = orgMapper.selectById(id);
        if (org == null) {
            throw new RuntimeException("组织部门不存在");
        }
        
        List<SysOrganization> children = orgMapper.selectChildren(id);
        if (!children.isEmpty()) {
            throw new RuntimeException("该组织部门下有子节点，不能删除");
        }
        
        int userCount = orgMapper.countUsers(id);
        if (userCount > 0) {
            throw new RuntimeException("该组织部门下有关联用户，不能删除");
        }
        
        orgMapper.deleteById(id);
        log.info("删除组织部门：{} ({})", org.getOrgName(), org.getOrgCode());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status) {
        SysOrganization org = new SysOrganization();
        org.setId(id);
        org.setStatus(status);
        org.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(org);
    }
    
    @Override
    public String getFullPathName(String id) {
        SysOrganization org = orgMapper.selectById(id);
        if (org == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder(org.getOrgName());
        String parentId = org.getParentId();
        
        while (!"0".equals(parentId)) {
            SysOrganization parent = orgMapper.selectById(parentId);
            if (parent == null) {
                break;
            }
            sb.insert(0, parent.getOrgName() + "/");
            parentId = parent.getParentId();
        }
        
        return sb.toString();
    }
    
    @Override
    public SysOrganization getUserOrg(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getOrgId())) {
            return null;
        }
        return orgMapper.selectById(user.getOrgId());
    }
    
    @Override
    public SysOrganization getUserDept(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getDeptId())) {
            return null;
        }
        return orgMapper.selectById(user.getDeptId());
    }
    
    /**
     * 计算层级和路径
     */
    private void calcLevelAndPath(SysOrganization org) {
        if ("0".equals(org.getParentId())) {
            org.setLevel(0);
            org.setPath("/0/" + (StringUtils.hasText(org.getId()) ? org.getId() : "temp") + "/");
        } else {
            SysOrganization parent = orgMapper.selectById(org.getParentId());
            if (parent == null) {
                throw new RuntimeException("父级组织部门不存在");
            }
            org.setLevel(parent.getLevel() + 1);
            String idPart = StringUtils.hasText(org.getId()) ? org.getId() : "temp";
            org.setPath(parent.getPath() + idPart + "/");
        }
    }
    
    /**
     * 更新子节点path
     */
    private void updateChildrenPath(String oldPath, String newPath) {
        if (oldPath.equals(newPath)) {
            return;
        }
        orgMapper.updateChildrenPath(oldPath, newPath);
        log.info("更新子节点path：{} -> {}", oldPath, newPath);
    }
    
    /**
     * 构建树形结构
     */
    private List<SysOrganization> buildTree(List<SysOrganization> list) {
        List<SysOrganization> rootList = list.stream()
                .filter(o -> "0".equals(o.getParentId()))
                .collect(Collectors.toList());
        
        for (SysOrganization root : rootList) {
            buildChildren(root, list);
        }
        
        return rootList.isEmpty() ? list : rootList;
    }
    
    /**
     * 递归构建子节点
     */
    private void buildChildren(SysOrganization parent, List<SysOrganization> allList) {
        List<SysOrganization> children = allList.stream()
                .filter(o -> parent.getId().equals(o.getParentId()))
                .collect(Collectors.toList());
        
        if (!children.isEmpty()) {
            parent.setChildren(children);
            for (SysOrganization child : children) {
                buildChildren(child, allList);
            }
        }
    }
}
