package com.workflow.contracts.dictionary.port;

import java.util.Map;

/** 字典资产应用边界；definition/items/parentItemCode 继续使用已发布配置包协议。 */
public interface DictionaryMigrationPort {
    /** 合并完整快照并按项编码重建父子关系；非法父项抛出异常并回滚当前事务。 */
    void apply(String dictionaryCode, Map<String, Object> snapshot);

    /** 回滚新建资产时停用目标字典；返回是否存在目标，编排据此登记新的资产快照。 */
    boolean disable(String dictionaryCode);

    /** 批量导入完成后刷新字典缓存，刷新时机由外层迁移事务编排保持。 */
    void refreshCache();
}
