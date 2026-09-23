package com.workflow.core.database.port;

import com.workflow.integration.database.api.SchemaColumnMetadata;
import com.workflow.integration.database.api.SchemaTableMetadata;
import com.workflow.integration.database.api.SchemaIndexMetadata;
import java.util.List;

/** 物理结构读取端口；连接或权限异常必须向上抛出，不能伪装成表不存在。 */
public interface SchemaMetadataPort {
    boolean tableExists(String table);
    List<SchemaColumnMetadata> columns(String table);
    List<SchemaTableMetadata> tables();
    List<SchemaIndexMetadata> indexes(String table);

    /** 获取发布风险评估所需行数。无统计信息时允许实际计数；不存在的表返回 0。 */
    long estimateRows(String table);
}
