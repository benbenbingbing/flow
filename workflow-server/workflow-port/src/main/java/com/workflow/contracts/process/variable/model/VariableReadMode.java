package com.workflow.contracts.process.variable.model;

/** 只有确认运行态退出才能回退历史，技术故障始终向上抛出。 */
public enum VariableReadMode { RUNTIME_ONLY, HISTORY_ONLY, RUNTIME_OR_HISTORY }
