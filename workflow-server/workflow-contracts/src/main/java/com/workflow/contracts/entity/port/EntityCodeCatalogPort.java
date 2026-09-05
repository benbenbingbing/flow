package com.workflow.contracts.entity.port;

import java.util.Set;

/**
 * 实体编码目录的稳定跨模块查询能力。
 *
 * <p>由实体领域实现，流程等调用方只通过该端口读取目录信息。</p>
 */
public interface EntityCodeCatalogPort {

    /**
     * 查询全部实体编码。
     *
     * @return 全部实体编码集合
     */
    Set<String> findAllEntityCodes();

    /**
     * 根据流程定义 ID 查询关联实体编码。
     *
     * @param processDefinitionId 流程定义 ID
     * @return 关联实体编码；不存在时返回 {@code null}
     */
    String findEntityCodeByProcessDefinitionId(String processDefinitionId);
}
