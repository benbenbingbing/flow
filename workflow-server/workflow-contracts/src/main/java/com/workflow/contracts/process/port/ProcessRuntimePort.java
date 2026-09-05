package com.workflow.contracts.process.port;

import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;

/**
 * 流程运行时跨模块端口。
 */
public interface ProcessRuntimePort {

    /**
     * 按流程启动请求创建并启动流程实例。
     *
     * @param request 流程启动请求
     * @return 启动结果
     */
    ProcessStartResult start(ProcessStartRequest request);
}
