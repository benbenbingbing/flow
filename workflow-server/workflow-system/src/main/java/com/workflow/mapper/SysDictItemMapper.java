package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysDictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 字典明细 Mapper
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 根据字典ID查询所有字典项（含已删除的，用于级联删除）
     */
    @Select("SELECT * FROM sys_dict_item WHERE dict_id = #{dictId}")
    List<SysDictItem> selectAllByDictId(@Param("dictId") String dictId);

    /**
     * 根据字典ID逻辑删除所有字典项
     */
    @Update("UPDATE sys_dict_item SET deleted = 1 WHERE dict_id = #{dictId} AND deleted = 0")
    int deleteByDictId(@Param("dictId") String dictId);

    /**
     * 根据父ID查询子项数量
     */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE parent_id = #{parentId} AND deleted = 0")
    int countChildren(@Param("parentId") String parentId);
}
