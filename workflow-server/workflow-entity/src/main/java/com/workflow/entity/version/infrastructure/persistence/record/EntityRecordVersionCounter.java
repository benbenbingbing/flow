package com.workflow.entity.version.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 同一实体记录的事务级版本号计数器。 */
@Data
@TableName("entity_record_version_counter")
public class EntityRecordVersionCounter {

    private String entityCode;
    private String recordId;
    private Integer lastVersionNo;
    private LocalDateTime updateTime;
}
