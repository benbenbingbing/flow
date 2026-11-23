package com.workflow.port.system;

import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.model.EntityRecordPage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅查询平台白名单内的系统实体，字段与数据权限由宿主维护。 */
public interface SystemEntityRecordPort {
    /** 按主键查询系统数据；不存在或不可见时与平台详情使用相同错误语义。 */
    EntityRecordData requireById(String entityCode, String recordId);

    /** 按受支持的系统字段筛选当前调用人可读的数据。 */
    EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize);

    /** 按输入顺序去重并批量回显可读记录，跳过缺失和已逻辑删除的记录。 */
    List<EntityRecordData> findByIds(String entityCode, List<String> recordIds);

    /**
     * 在授权范围内按允许的字段排序分页。fields 为空引用时保留所有可读字段，
     * 否则仅投影指定的 data 字段，记录 ID、编码、名称等基本信息始终保留。
     */
    EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize,
            String sortField, boolean descending, Set<String> fields);
}
