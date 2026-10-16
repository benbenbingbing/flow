package com.workflow.contracts.process.instance.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 接收任务触发回执，明确实际推进的执行分支。
 * @param receipt 持久化命令回执
 * @param executionId 被触发的执行实例 ID
 */
public record ReceiveTaskTriggerResult(
        ProcessCommandReceipt receipt,
        String executionId) {

}
