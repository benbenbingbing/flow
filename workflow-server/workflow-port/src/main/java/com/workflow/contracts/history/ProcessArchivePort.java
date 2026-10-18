package com.workflow.contracts.history;

import java.util.List;
import java.util.Map;
import com.workflow.contracts.history.HistoryImportModels.*;

/** 流程模块保存独立历史档案；不会创建引擎实例、任务或执行流程动作。 */
public interface ProcessArchivePort {
    /** 提供已发布图版本供导入配置选择，返回内容仅用于固定历史展示。 */
    List<ProcessTemplate> templates();
    /** 获取指定发布版本的 XML 和节点索引，不启动或转换引擎实例。 */
    ProcessTemplate template(String id);
    /** 在实体导入的同一事务中保存；重复来源且内容相同返回原档案。 */
    String save(Archive archive);
    /** 对主实体执行当前数据范围授权后，列出该记录的全部迁入流程轮次。 */
    List<Map<String, Object>> list(String entityCode, String recordId);
    /** 逐次授权主记录及关联快照；只返回当前可见字段，禁止直接向客户端暴露原始归档 JSON。 */
    Map<String, Object> detail(String archiveId);
}
