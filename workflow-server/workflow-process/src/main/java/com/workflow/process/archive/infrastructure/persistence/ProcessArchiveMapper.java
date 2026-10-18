package com.workflow.process.archive.infrastructure.persistence;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** 独立归档表，不读取或写入流程引擎运行态。 */
@Mapper
public interface ProcessArchiveMapper {
    record SourceMatch(String id, String contentHash) {}
    record Header(String id, String name, String result, String sourceSystem, String sourceId) {}
    @Select("SELECT id,content_hash FROM process_archive_instance WHERE source_hash=#{hash}")
    List<SourceMatch> findSource(@Param("hash") String hash);
    @Select("SELECT document_json FROM process_archive_instance WHERE id=#{id}")
    String document(@Param("id") String id);
    @Select("SELECT id,process_name,end_result,source_system,source_instance_id FROM process_archive_instance WHERE entity_code=#{entityCode} AND entity_record_id=#{recordId} ORDER BY created_at,id")
    List<Header> list(@Param("entityCode") String entityCode, @Param("recordId") String recordId);
    @Insert("INSERT INTO process_archive_instance(id,source_hash,source_system,source_instance_id,entity_code,entity_record_id,process_name,end_result,content_hash,document_json,created_at) VALUES (#{id},#{sourceHash},#{system},#{sourceId},#{entityCode},#{recordId},#{name},#{result},#{contentHash},#{document},CURRENT_TIMESTAMP)")
    void insert(@Param("id") String id, @Param("sourceHash") String sourceHash, @Param("system") String system,
                @Param("sourceId") String sourceId, @Param("entityCode") String entityCode, @Param("recordId") String recordId,
                @Param("name") String name, @Param("result") String result, @Param("contentHash") String contentHash, @Param("document") String document);
}
