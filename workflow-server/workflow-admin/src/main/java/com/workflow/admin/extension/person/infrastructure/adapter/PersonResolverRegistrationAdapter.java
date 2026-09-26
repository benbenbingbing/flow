package com.workflow.admin.extension.person.infrastructure.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.admin.extension.person.infrastructure.persistence.mapper.PersonResolverDefinitionMapper;
import com.workflow.admin.extension.person.infrastructure.persistence.record.PersonResolverDefinition;
import com.workflow.contracts.process.assignment.port.PersonResolverRegistrationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 目录治理留在 admin；流程扩展注册本身不能绕过启用检查。 */
@Component
@RequiredArgsConstructor
public class PersonResolverRegistrationAdapter implements PersonResolverRegistrationPort {
    private final PersonResolverDefinitionMapper mapper;

    /** 复用 resolver_code/deleted 的唯一约束，保持原有目录查询语义。 */
    @Override
    public boolean isEnabled(String resolverCode) {
        PersonResolverDefinition definition = mapper.selectOne(
                new LambdaQueryWrapper<PersonResolverDefinition>()
                        .eq(PersonResolverDefinition::getResolverCode, resolverCode)
                        .eq(PersonResolverDefinition::getDeleted, 0));
        return definition != null && Boolean.TRUE.equals(definition.getEnabled());
    }
}
