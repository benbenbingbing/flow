package com.workflow.service.permission;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.workflow.entity.EntityListPermission;
import com.workflow.mapper.EntityListPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 实体列表数据权限规则服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityListPermissionService extends ServiceImpl<EntityListPermissionMapper, EntityListPermission> {

    private final EntityListPermissionMapper permissionMapper;

    /**
     * 查询某实体下所有启用的规则
     */
    public List<EntityListPermission> findByEntityCode(String entityCode) {
        return permissionMapper.findEnabledByEntityCode(entityCode);
    }
}
