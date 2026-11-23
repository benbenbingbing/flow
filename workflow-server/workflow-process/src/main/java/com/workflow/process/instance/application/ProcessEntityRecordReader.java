package com.workflow.process.instance.application;

import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.mutation.error.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 宿主流程从已经验证的引擎业务坐标读取记录，供子流程绑定和任务表单使用。 */
@Component
@RequiredArgsConstructor
public class ProcessEntityRecordReader {
    private final EntityDataDynamicService records;

    /** 调用者必须先核验流程或任务边界，再使用宿主取得的实体坐标。 */
    public EntityRecordData findById(String entityCode, String recordId) {
        try {
            return EntityRecordProjection.project(records.findById(entityCode, recordId));
        } catch (EntityMutationTargetNotFoundException missing) {
            return null;
        }
    }
}
