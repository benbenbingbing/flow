package com.workflow.migration.application;

import java.util.Map;

/**
 * 发布候选对真实配置部署能力的端口。
 * 实现必须自行声明事务边界和幂等语义，编排层不会承诺跨模块全局事务。
 */
public interface ReleaseCandidateDeploymentPort {

    /** 发布绑定的配置迁移导入批次。 */
    Map<String, Object> publishImport(String importId);

    /** 回滚绑定的配置迁移导入批次。 */
    Map<String, Object> rollbackImport(String importId);
}
