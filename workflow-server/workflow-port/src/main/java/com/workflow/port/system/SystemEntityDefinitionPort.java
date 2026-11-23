package com.workflow.port.system;

import java.util.List;

/** 系统实体只读目录，可用范围与 SystemEntityRecordPort 一致。 */
public interface SystemEntityDefinitionPort {
    /** 列出当前身份可用于列表和选择器的已登记系统实体，按编码排序。 */
    List<Summary> listReadable();

    /** 查询可读取、筛选和排序的字段，实体不可读时拒绝访问。 */
    List<Field> describeReadableFields(String entityCode);

    /** displayField 是首选显示字段，可能为空。 */
    record Summary(String entityCode, String entityName, String displayField) { }

    /** code 可直接用于构建系统记录筛选或字段投影，type 为平台实体字段类型。 */
    record Field(String code, String name, String type) { }
}
