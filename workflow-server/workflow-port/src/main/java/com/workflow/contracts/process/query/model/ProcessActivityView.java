package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 当前执行位置；同一节点允许多个执行分支。
 * @param activityId BPMN 节点 ID
 * @param activityType 节点类型
 * @param executionId 定位具体等待分支所需的执行 ID
 * @param active 是否仍处于活动位置
 */
public record ProcessActivityView(
        String activityId,
        String activityType,
        String executionId,
        boolean active) {

}
