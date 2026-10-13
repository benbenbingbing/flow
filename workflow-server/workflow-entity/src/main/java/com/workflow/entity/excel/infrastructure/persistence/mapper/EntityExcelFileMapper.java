package com.workflow.entity.excel.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.*;
import org.springframework.util.LinkedCaseInsensitiveMap;
import java.time.LocalDateTime;

/** 私有 Excel 任务文件登记；物理对象删除成功后才更新逻辑删除标记，供失败重试使用。 */
@Mapper
public interface EntityExcelFileMapper {
    @Insert("""
            INSERT INTO storage_file_object(id,storage_url,storage_key,owner_user_id,original_name,
              content_type,content_length,deleted,create_time,update_time)
            VALUES(#{id},#{url},#{key},#{owner},#{name},#{contentType},#{size},0,#{now},#{now})
            """)
    int insert(@Param("id") String id, @Param("url") String url, @Param("key") String key,
            @Param("owner") String owner, @Param("name") String name, @Param("contentType") String contentType,
            @Param("size") long size, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM storage_file_object WHERE id=#{id} AND deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    LinkedCaseInsensitiveMap<Object> find(@Param("id") String id);

    @Select("SELECT storage_url FROM storage_file_object WHERE id=#{id} AND deleted=0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    String activeUrl(@Param("id") String id);

    @Update("UPDATE storage_file_object SET deleted=1,update_time=#{now} WHERE id=#{id}")
    int markDeleted(@Param("id") String id, @Param("now") LocalDateTime now);
}
