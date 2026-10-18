package com.workflow.migration.history.infrastructure.persistence;

import org.apache.ibatis.annotations.*;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 批次锁、提交组和预检快照共享业务事务；SQL 参数不接受客户端列名。 */
@Mapper
public interface HistoryImportBatchMapper {
    record Batch(String id, String owner, String name, String state, int revision, String plan,
                 String entities, String history, String validation, String validationHash) {}
    record Summary(String id, String name, String state, int revision, String createdAt) {}
    record Group(String groupKey, String state, String result, String error) {}
    String COLUMNS = "id,owner_id,name,state,revision,plan_json,entities_json,history_json,validation_json,validation_hash";

    @Select("SELECT " + COLUMNS + " FROM history_import_batch WHERE id=#{id}")
    Batch get(@Param("id") String id);
    @Select("SELECT " + COLUMNS + " FROM history_import_batch WHERE id=#{id} FOR UPDATE")
    Batch lock(@Param("id") String id);
    @Select("SELECT id,name,state,revision,created_at FROM history_import_batch WHERE owner_id=#{owner} ORDER BY created_at DESC,id")
    IPage<Summary> list(Page<Summary> page, @Param("owner") String owner);
    @Insert("INSERT INTO history_import_batch(id,owner_id,name,state,revision,plan_json,created_at,updated_at) VALUES (#{id},#{owner},#{name},'DRAFT',1,#{plan},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    void create(@Param("id") String id, @Param("owner") String owner, @Param("name") String name, @Param("plan") String plan);
    @Update("UPDATE history_import_batch SET name=#{name},plan_json=#{plan},validation_json=NULL,validation_hash=NULL,state='UPLOADED',revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    void plan(@Param("id") String id, @Param("name") String name, @Param("plan") String plan);
    @Update("<script>UPDATE history_import_batch SET <choose><when test='kind == &quot;entities&quot;'>entities_json</when><otherwise>history_json</otherwise></choose>=#{input},validation_json=NULL,validation_hash=NULL,state='UPLOADED',revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}</script>")
    void upload(@Param("id") String id, @Param("kind") String kind, @Param("input") String input);
    @Update("UPDATE history_import_batch SET validation_json=#{document},validation_hash=#{hash},state='READY',revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    void ready(@Param("id") String id, @Param("document") String document, @Param("hash") String hash);
    @Update("UPDATE history_import_batch SET state=#{state},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
    void state(@Param("id") String id, @Param("state") String state);
    @Delete("DELETE FROM history_import_group WHERE batch_id=#{id}")
    void deleteGroups(@Param("id") String id);
    @Insert("INSERT INTO history_import_group(id,batch_id,group_key,group_hash,state,updated_at) VALUES (#{id},#{batchId},#{key},#{hash},'PENDING',CURRENT_TIMESTAMP)")
    void createGroup(@Param("id") String id, @Param("batchId") String batchId, @Param("key") String key, @Param("hash") String hash);
    @Select("SELECT group_key,state,result_json,error_message FROM history_import_group WHERE batch_id=#{id} ORDER BY group_key")
    List<Group> groups(@Param("id") String id);
    @Update("UPDATE history_import_group SET state='COMMITTED',result_json=#{result},error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE batch_id=#{id} AND group_hash=#{hash}")
    void commitGroup(@Param("id") String id, @Param("hash") String hash, @Param("result") String result);
    @Update("UPDATE history_import_group SET state='FAILED',error_message=#{error},updated_at=CURRENT_TIMESTAMP WHERE batch_id=#{id} AND group_hash=#{hash} AND state<>'COMMITTED'")
    int failGroup(@Param("id") String id, @Param("hash") String hash, @Param("error") String error);
    @Select("SELECT COUNT(*) FROM history_import_group WHERE batch_id=#{id} AND state='COMMITTED'")
    int committed(@Param("id") String id);
    @Select("SELECT COUNT(*) FROM history_import_group WHERE batch_id=#{id} AND state<>'COMMITTED'")
    int pending(@Param("id") String id);
}
