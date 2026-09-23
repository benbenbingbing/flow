package com.workflow.entity.ui.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.ui.infrastructure.persistence.record.UiViewComposition;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * “关联内容”草稿持久化入口。
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface UiViewCompositionMapper
        extends BaseMapper<UiViewComposition> {

    /** 按宿主稳定排序读取全部活动关联内容。 */
    default List<UiViewComposition> findByOwner(String ownerType, String ownerId) {
        return selectList(Wrappers.<UiViewComposition>lambdaQuery()
                .eq(UiViewComposition::getOwnerType, ownerType)
                .eq(UiViewComposition::getOwnerId, ownerId)
                .orderByAsc(UiViewComposition::getOrderKey)
                .orderByAsc(UiViewComposition::getCompositionKey)
                .orderByAsc(UiViewComposition::getId));
    }

    /**
     * 锁定宿主的全部关联内容（包含逻辑删除行）。
     *
     * <p>发布和撤销以宿主为串行化边界；包含历史删除行可以避免恢复期间
     * 与同一业务编码的并发新增交错。</p>
     */
    @Select("SELECT * FROM ui_view_composition "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId} "
            + "ORDER BY order_key, composition_key, id FOR UPDATE")
    List<UiViewComposition> findByOwnerForUpdate(
            @Param("ownerType") String ownerType,
            @Param("ownerId") String ownerId);

    /** 按宿主和稳定业务编码查询当前活动记录。 */
    default UiViewComposition findActiveByKey(String ownerType, String ownerId, String compositionKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<UiViewComposition>lambdaQuery()
                .eq(UiViewComposition::getOwnerType, ownerType)
                .eq(UiViewComposition::getOwnerId, ownerId)
                .eq(UiViewComposition::getCompositionKey, compositionKey))
                .stream().findFirst().orElse(null);
    }

    /** 更新前锁定单条活动记录。 */
    @Select("SELECT * FROM ui_view_composition "
            + "WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UiViewComposition selectByIdForUpdate(@Param("id") String id);

    /**
     * 物理清理宿主草稿，供发布快照恢复使用。
     *
     * <p>发布快照保存在独立不可变表中；恢复前物理清理草稿可以避免历史
     * 逻辑删除行与被恢复的稳定 ID 或业务编码冲突。</p>
     */
    @Delete("DELETE FROM ui_view_composition "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId}")
    int deleteByOwner(
            @Param("ownerType") String ownerType,
            @Param("ownerId") String ownerId);
}
