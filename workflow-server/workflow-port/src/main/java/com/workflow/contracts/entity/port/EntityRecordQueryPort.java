package com.workflow.contracts.entity.port;

import com.workflow.contracts.entity.model.EntityRecordData;
import java.util.List;
import java.util.Map;

/** 保留宿主实体读取与数据权限语义的业务查询入口；调用方不得自行查询动态表。 */
public interface EntityRecordQueryPort {
    /** 按实体编码和记录 ID 查询；不存在时返回 null，授权失败保留宿主异常。 */
    EntityRecordData findById(String entityCode, String recordId);

    /** 使用发布字段及当前身份的数据权限查询；条件格式与实体运行时保持一致。 */
    List<EntityRecordData> findByCondition(String entityCode, Map<String, Object> condition);
}
