package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 配置导入条目 Mapper。
 *
 * <p>提供 config_import_item 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigImportItemMapper extends BaseMapper<ConfigImportItem> {

    /**
     * 持久化重新分析结果，并显式允许清空过期错误信息。
     *
     * @param item 已完成比较和依赖分析的导入条目
     * @return 影响行数
     */
    @Update("""
            UPDATE config_import_item
            SET target_before_version = #{targetBeforeVersion},
                target_before_hash = #{targetBeforeHash},
                comparison_status = #{comparisonStatus},
                mapping_status = #{mappingStatus},
                error_message = #{errorMessage},
                update_time = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateAnalysisResult(ConfigImportItem item);
}
