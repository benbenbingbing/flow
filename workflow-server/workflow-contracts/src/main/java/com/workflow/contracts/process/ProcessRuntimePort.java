package com.workflow.contracts.process;

/**
 * 流程运行时跨模块端口。
 */
public interface ProcessRuntimePort {

    ProcessStartResult start(ProcessStartRequest request);
}
