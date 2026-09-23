package com.workflow.admin.dictionary.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 字典明细 Mapper
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 根据字典ID查询所有字典项（含已删除的，用于级联删除）
     *
     * @param dictId 字典ID
     * @return 字典项列表
     */
    @Select("SELECT * FROM sys_dict_item WHERE dict_id = #{dictId}")
    List<SysDictItem> selectAllByDictId(@Param("dictId") String dictId);

    /**
     * 根据字典ID逻辑删除所有字典项
     *
     * @param dictId 字典ID
     * @return 受影响的记录数
     */
    default int deleteByDictId(String dictId) {
        // 字典项使用统一逻辑删除配置，BaseMapper 生成 deleted 更新而非物理删除。
        return delete(Wrappers.<SysDictItem>lambdaQuery().eq(SysDictItem::getDictId, dictId));
    }

    /**
     * 根据父ID查询子项数量
     *
     * @param parentId 父项ID
     * @return 子项数量
     */
    default int countChildren(String parentId) {
        return selectCount(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getParentId, parentId)).intValue();
    }

    default SysDictItem selectEnabledByCode(String dictCode, String itemCode) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysDictItem>(1, 1, false), Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictCode, dictCode)
                .eq(SysDictItem::getItemCode, itemCode)
                .eq(SysDictItem::getStatus, "0")).getRecords().stream().findFirst().orElse(null);
    }

    /** 查询指定字典下可选的有效条目，按配置顺序及条目编码返回。 */
    default List<SysDictItem> selectEnabledByDictCode(String dictCode) {
        return selectList(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictCode, dictCode)
                .eq(SysDictItem::getStatus, "0")
                .orderByAsc(SysDictItem::getSort)
                .orderByAsc(SysDictItem::getItemCode));
    }
}
