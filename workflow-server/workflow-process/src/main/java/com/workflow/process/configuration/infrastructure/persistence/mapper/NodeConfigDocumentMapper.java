package com.workflow.process.configuration.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.*;

/** 节点扩展配置的局部读写；由同步服务合并 JSON，避免覆盖同一节点的其他配置。 */
@Mapper
public interface NodeConfigDocumentMapper {
    /** 返回持久化文档；不存在时返回 null，由更新行数校验拦截失效节点。 */
    @Select("SELECT config_json FROM process_node_config WHERE id = #{id}")
    String findDocument(@Param("id") String id);

    /** 与节点同步事务一起提交；调用方必须确认恰好更新一行。 */
    @Update("UPDATE process_node_config SET config_json = #{document} WHERE id = #{id}")
    int updateDocument(@Param("document") String document, @Param("id") String id);
}
