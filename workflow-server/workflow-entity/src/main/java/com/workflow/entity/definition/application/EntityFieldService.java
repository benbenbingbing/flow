package com.workflow.entity.definition.application;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import org.springframework.stereotype.Service;

/**
 * 实体字段服务
 */
@Service
public class EntityFieldService extends ServiceImpl<EntityFieldMapper, EntityField> {
}
