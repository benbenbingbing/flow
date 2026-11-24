package com.workflow.port.migration;

import java.util.List;
import java.util.Map;
import com.workflow.port.migration.model.history.HistoryImportModels.*;

/** 流程模块保存独立历史档案，由调用方授权档案和关联记录。 */
public interface ProcessArchiveInstancePort {
    /** 读操作。提供已发布图版本供导入配置选择，返回内容仅用于固定历史展示。 */
    List<ProcessTemplate> templates();
    /** 读操作。获取指定发布版本的 XML 和节点索引，不启动或转换引擎实例。 */
    ProcessTemplate template(String id);
    /** 写操作。在实体导入的同一事务中保存；重复来源且内容相同返回原档案。 */
    String save(Archive archive);
    /** 读操作。列出已授权记录的全部迁入流程轮次。 */
    List<Map<String, Object>> list(String entityCode, String recordId);
    /** 读操作。返回归档时的主记录及关联快照，由调用方控制字段与记录的披露范围。 */
    Map<String, Object> detail(String archiveId);
}
