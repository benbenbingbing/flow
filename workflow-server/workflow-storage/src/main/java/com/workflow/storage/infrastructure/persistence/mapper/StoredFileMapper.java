package com.workflow.storage.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.core.database.mybatis.ParameterizedSqlProvider;
import org.apache.ibatis.annotations.*;

/** 存储对象归属与幂等登记。调用方只传入服务端 SQL 和绑定值，不接受客户端 SQL。 */
@Mapper
public interface StoredFileMapper {
    /** 按唯一地址读取有效对象所有者；不存在时返回 null。 */
    @Select("SELECT owner_user_id FROM storage_file_object WHERE storage_url = #{url} AND deleted = 0")
    String findOwner(@Param("url") String url);

    /** 按用户幂等键读取上传登记，冲突后追加方言当前读守卫。 */
    @SelectProvider(type = ParameterizedSqlProvider.class, method = "sql")
    StoredUploadRow findUpload(@Param("statement") ParameterizedSql statement);

    /** 登记或逻辑删除；跟随应用事务，插入冲突仍使用原保存点保护。 */
    @UpdateProvider(type = ParameterizedSqlProvider.class, method = "sql")
    int execute(@Param("statement") ParameterizedSql statement);

    /** 列顺序固定，与查询投影一致；元信息用于幂等重放原响应。 */
    record StoredUploadRow(String storageUrl, String storageKey, String originalName,
                           long contentLength, String requestHash, boolean deleted) {}
}
