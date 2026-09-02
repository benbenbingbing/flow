package com.workflow.contracts.process;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 流程目录查询端口。
 * 供其他模块按流程ID查询流程名称与目录条目，屏蔽流程持久化层的实现细节。
 */
public interface ProcessCatalogPort {

    /**
     * 根据流程ID集合批量查询流程名称。
     *
     * @param processIds 流程ID集合
     * @return 流程ID到流程名称的映射
     */
    Map<String, String> findNamesByIds(Collection<String> processIds);

    /**
     * 根据流程ID集合批量查询流程目录条目。
     *
     * @param processIds 流程ID集合
     * @return 流程ID到目录条目的映射，默认返回空映射
     */
    default Map<String, ProcessCatalogItem> findItemsByIds(Collection<String> processIds) {
        return Collections.emptyMap();
    }

    /**
     * 在当前事务中按传入顺序锁定流程配置，并返回绑定生命周期状态。
     *
     * <p>调用方必须按稳定顺序传入去重后的 ID。实现需要与流程发布共用同一数据库
     * 行锁，使“发布”与“绑定、解绑、删除实体”不能交错提交。</p>
     *
     * @param processIds 需要锁定的流程配置 ID
     * @return 实际存在的流程状态映射，不存在的 ID 不返回
     */
    default Map<String, ProcessBindingState> lockBindingStates(
            Collection<String> processIds) {
        throw new UnsupportedOperationException(
                "当前流程目录实现不支持绑定状态锁定");
    }
}
