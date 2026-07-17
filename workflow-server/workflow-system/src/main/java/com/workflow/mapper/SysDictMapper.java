package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典类型 Mapper
 */
@Mapper
public interface SysDictMapper extends BaseMapper<SysDict> {

    /**
     * 检查字典编码是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_dict WHERE dict_code = #{dictCode} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsDictCode(@Param("dictCode") String dictCode, @Param("excludeId") String excludeId);
}
