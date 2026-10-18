package com.workflow.entity.history.infrastructure.persistence;

import com.workflow.contracts.history.HistoryImportModels.SourceRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 来源身份和只读策略与业务行使用相同数据源事务，摘要唯一键负责并发兜底。 */
@Mapper
public interface EntityHistorySourceMapper {
    @Select("SELECT entity_code,entity_record_id,content_hash FROM entity_import_source_link WHERE source_hash=#{hash}")
    List<SourceRecord> find(@Param("hash") String hash);

    @Select("SELECT COUNT(*) FROM entity_import_source_link WHERE entity_code=#{entityCode} AND entity_record_id=#{recordId} AND readonly_flag=1")
    int readonlyCount(@Param("entityCode") String entityCode, @Param("recordId") String recordId);

    @Insert("INSERT INTO entity_import_source_link(source_hash,source_system,source_entity_key,source_record_id,entity_code,entity_record_id,content_hash,readonly_flag,batch_id,created_at) VALUES (#{hash},#{system},#{key},#{sourceId},#{entityCode},#{recordId},#{contentHash},#{readonly},#{batchId},CURRENT_TIMESTAMP)")
    void insert(@Param("hash") String hash, @Param("system") String system, @Param("key") String key,
                @Param("sourceId") String sourceId, @Param("entityCode") String entityCode, @Param("recordId") String recordId,
                @Param("contentHash") String contentHash, @Param("readonly") int readonly, @Param("batchId") String batchId);
}
