package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** V2 版本中的一个一层关系数据集。 */
@Data
@TableName("entity_record_version_dataset")
public class EntityRecordVersionDataset {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String versionId;
    private String nodeCode;
    private String nodeKind;
    private String relationCode;
    private String relationName;
    private String entityCode;
    private String entityName;
    private String entityReleaseId;
    private Integer entityReleaseVersion;
    private String selectorDocument;
    private String presentationDocument;
    private String dataHash;
    private String presentationHash;
    private String scopeHash;
    private Integer rowCount;
    private Boolean complete;
    private LocalDateTime createTime;
}
