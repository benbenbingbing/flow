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
     *
     * @param dictCode  字典编码
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串）
     * @return 存在返回 true，否则 false
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_dict WHERE dict_code = #{dictCode} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsDictCode(@Param("dictCode") String dictCode, @Param("excludeId") String excludeId);
}
