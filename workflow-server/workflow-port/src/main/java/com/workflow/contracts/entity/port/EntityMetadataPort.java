package com.workflow.contracts.entity.port;

import com.workflow.contracts.dictionary.model.DictionaryEntry;
import com.workflow.contracts.entity.model.EntityPublishedMetadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 查询供业务模块使用的发布定义；调用方可通过发布 ID 关联具体快照。 */
public interface EntityMetadataPort {
    /** 精确表单来源供宿主检查字段绑定、配置版本和记录范围。 */
    record FieldOptionsRequest(String entityCode, String fieldCode, String formId, String configHash,
            String recordId, Map<String, Object> input) { }

    Optional<EntityPublishedMetadata> findPublished(String entityCode);

    /** 查询指定发布字段的有效字典选项。接口选项的绑定 ID 由发布字段元数据返回。 */
    List<DictionaryEntry> listDictionaryOptions(String entityCode, String fieldCode);

    /** 调用表单已发布的字段选项 READ 接口；返回值遵循该接口的选项响应结构。 */
    Object loadInterfaceOptions(FieldOptionsRequest request);
}
