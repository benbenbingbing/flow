package com.workflow.admin.setting.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.setting.infrastructure.persistence.record.GlobalSettingRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 设置存取入口；所有可变操作同时限定归属、稳定键、记录 ID 和版本。 */
@Mapper
public interface GlobalSettingMapper extends BaseMapper<GlobalSettingRecord> {
    /** 读取作用域内单个设置；不存在时返回 null，不自动创建偏好。 */
    @Select("SELECT * FROM sys_global_setting WHERE scope_type = #{scope} AND owner_id = #{owner} AND setting_key = #{key}")
    GlobalSettingRecord find(@Param("scope") String scope, @Param("owner") String owner, @Param("key") String key);

    /** 批量读取一个归属下的设置，用于系统设置列表。 */
    @Select("SELECT * FROM sys_global_setting WHERE scope_type = #{scope} AND owner_id = #{owner}")
    List<GlobalSettingRecord> findAll(@Param("scope") String scope, @Param("owner") String owner);

    /** 原子更新并递增版本，返回 0 表示已被修改或删除重建；不更改记录归属与键。 */
    @Update("""
            UPDATE sys_global_setting
            SET setting_value = #{row.settingValue}, setting_value_type = #{row.settingValueType},
                name = #{row.name}, remark = #{row.remark},
                version = version + 1, updated_by = #{row.updatedBy}, update_time = #{row.updateTime}
            WHERE id = #{row.id} AND version = #{row.version}
              AND scope_type = #{row.scopeType} AND owner_id = #{row.ownerId} AND setting_key = #{row.settingKey}
            """)
    int updateValue(@Param("row") GlobalSettingRecord row);

    /** 删除指定版本恢复继承；旧页面不能删除另一用户或删除重建后的记录。 */
    @Delete("""
            DELETE FROM sys_global_setting WHERE id = #{id} AND version = #{version}
              AND scope_type = #{scope} AND owner_id = #{owner} AND setting_key = #{key}
            """)
    int deleteVersion(@Param("scope") String scope, @Param("owner") String owner,
                      @Param("key") String key, @Param("id") String id, @Param("version") long version);
}
