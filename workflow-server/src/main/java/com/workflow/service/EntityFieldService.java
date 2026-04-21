package com.workflow.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.workflow.entity.EntityField;
import com.workflow.mapper.EntityFieldMapper;
import org.springframework.stereotype.Service;

/**
 * 实体字段服务
 */
@Service
public class EntityFieldService extends ServiceImpl<EntityFieldMapper, EntityField> {
}
