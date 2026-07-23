package com.workflow.contracts.entity;

import java.util.Set;

/**
 * 实体编码目录端口。
 * 供其他模块查询系统中全部实体编码，以及按流程定义ID反查关联的实体编码。
 */
public interface EntityCodeCatalogPort {

    /**
     * 查询所有实体编码集合。
     *
     * @return 全部实体编码集合
     */
    Set<String> findAllEntityCodes();

    /**
     * 根据流程定义ID查询关联的实体编码。
     *
     * @param processDefinitionId 流程定义ID
     * @return 关联的实体编码，不存在时返回 null
     */
    String findEntityCodeByProcessDefinitionId(String processDefinitionId);
}
