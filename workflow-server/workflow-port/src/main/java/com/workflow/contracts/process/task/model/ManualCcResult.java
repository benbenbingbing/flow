package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 人工知会结果，只统计本次新增数量。
 * @param receipt 持久化命令回执
 * @param created 新增知会记录数
 */
public record ManualCcResult(
        ProcessCommandReceipt receipt,
        int created) {

}
