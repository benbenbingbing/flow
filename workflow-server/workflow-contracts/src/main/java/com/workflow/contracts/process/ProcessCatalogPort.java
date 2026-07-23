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
}
