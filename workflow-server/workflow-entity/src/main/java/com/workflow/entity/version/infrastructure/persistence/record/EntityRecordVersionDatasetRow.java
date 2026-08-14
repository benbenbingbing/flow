package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** V2 关系数据集中一条冻结记录。 */
@Data
@TableName("entity_record_version_dataset_row")
public class EntityRecordVersionDatasetRow {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String datasetId;
    private String recordId;
    private String recordTitle;
    private Integer rowOrder;
    private String rowHash;
    private String valuesDocument;
    private LocalDateTime createTime;
}
