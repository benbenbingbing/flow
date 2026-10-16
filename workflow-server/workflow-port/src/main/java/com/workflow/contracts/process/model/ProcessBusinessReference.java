package com.workflow.contracts.process.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 定位现有业务记录的流程关联，不授予实体读写权限。
 * @param entityCode 关联实体编码
 * @param entityRecordId 关联业务记录 ID
 */
public record ProcessBusinessReference(
        String entityCode,
        String entityRecordId) {

}
