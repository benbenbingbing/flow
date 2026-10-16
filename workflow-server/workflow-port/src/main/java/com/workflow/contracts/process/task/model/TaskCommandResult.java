package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 任务操作结果；加签子任务处理不等同于主流程推进。
 * @param receipt 持久化命令回执
 * @param taskStatus TODO、COMPLETED、TRANSFERRED、WAITING_ADD_SIGN、ADD_SIGN_COMPLETED 或 ADD_SIGN_CANCELLED；
 *                   TODO 也可能表示加签已收口、源办理人仍需确认下一审批人
 * @param processLifecycle 本次执行后的实例状态
 */
public record TaskCommandResult(
        ProcessCommandReceipt receipt,
        String taskStatus,
        ProcessLifecycle processLifecycle) {

}
