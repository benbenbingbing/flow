package com.workflow.contracts.history;

import java.util.List;
import java.util.Map;
import com.workflow.contracts.history.HistoryImportModels.*;

/** 实体模块负责历史实体创建与来源去重，调用方不得直接写动态业务表。 */
public interface EntityHistoryImportPort {
    /** 返回当前用户可导入的发布实体元数据，供生成标准模板与引用配置。 */
    List<EntitySchema> schemas();
    /** 读取发布快照及指纹；执行前重读指纹以识别预检后的配置漂移。 */
    EntitySchema schema(String entityCode);
    /** 执行前验证写权限、动态表已就绪及不存在会调用业务扩展的自定义取号。 */
    void validateImport(String entityCode);
    /** 按稳定来源身份查找并复核目标记录可读性；不存在返回 null，禁止借映射绕过数据范围。 */
    SourceRecord findSource(String sourceSystem, String entityKey, String sourceId);
    /** 校验允许字段、类型和关联字段定义；不执行任何表单业务事件。 */
    Map<String, Object> normalize(EntitySchema schema, Map<String, Object> values);
    /** 必须加入调用方事务；sourceHash 来自旧值及方案，而非转换后的新主键。 */
    EntityResult create(String sourceSystem, EntitySpec spec, EntityRow row, String sourceHash,
                        Map<String, Object> values, String batchId);
    /** 获取当前权限内可读字段，供归档快照展示时再次过滤。 */
    Map<String, Object> readable(String entityCode, String recordId);
}
