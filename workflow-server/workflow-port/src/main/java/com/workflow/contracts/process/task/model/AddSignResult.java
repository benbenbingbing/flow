package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 新增加签编排的执行结果。
 * @param receipt 持久化命令回执
 * @param addSignId 新编排 ID
 * @param taskIds 生成的本地任务 ID
 */
public record AddSignResult(
        ProcessCommandReceipt receipt,
        String addSignId,
        List<String> taskIds) {
    public AddSignResult {
        taskIds = List.copyOf(taskIds);

    }

}
