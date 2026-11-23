package com.workflow.contracts.entity.port;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.model.EntityRecordPage;

import java.util.Map;

/** 仅查询平台白名单内的系统实体，字段与数据权限由宿主维护。 */
public interface SystemEntityQueryPort {
    /** 按主键查询系统数据；不存在或不可见时与平台详情使用相同错误语义。 */
    EntityRecordData requireById(String entityCode, String recordId);

    /** 按受支持的系统字段筛选当前调用人可读的数据。 */
    EntityRecordPage page(String entityCode, Map<String, Object> filters, int pageNumber, int pageSize);
}
