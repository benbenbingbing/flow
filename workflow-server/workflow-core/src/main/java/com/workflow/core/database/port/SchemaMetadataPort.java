package com.workflow.core.database.port;

import com.workflow.integration.database.api.schema.SchemaColumnMetadata;
import com.workflow.integration.database.api.schema.SchemaTableMetadata;
import com.workflow.integration.database.api.schema.SchemaIndexMetadata;
import java.util.List;

/** 物理结构读取端口；连接或权限异常必须向上抛出，不能伪装成表不存在。 */
public interface SchemaMetadataPort {
    /**
     * 判断表存在条件是否成立，供调用方选择后续分支。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 表存在条件成立时为 true，否则为 false
     */
    boolean tableExists(String table);
    /**
     * 整理列集合数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 结构列元数据集合，供调用方遍历或展示
     */
    List<SchemaColumnMetadata> columns(String table);
    /**
     * 整理{@code tables}数据，供调用方遍历或继续处理。
     *
     * @return 结构表元数据集合，供调用方遍历或展示
     */
    List<SchemaTableMetadata> tables();
    /**
     * 整理{@code indexes}数据，供调用方遍历或继续处理。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 结构索引元数据集合，供调用方遍历或展示
     */
    List<SchemaIndexMetadata> indexes(String table);

    /**
     * 获取发布风险评估所需行数。无统计信息时允许实际计数；不存在的表返回 0。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 处理后的{@code estimate}行结果，供调用方继续处理
     */
    long estimateRows(String table);
}
