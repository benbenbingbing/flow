package com.workflow.contracts.process.port;

import com.workflow.contracts.process.model.ProcessStartRequest;
import com.workflow.contracts.process.model.ProcessStartResult;

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

    /**
     * 判断实体最新实例是否为该用户发起并已撤回，用于按钮能力查询。
     * 不代表已获得实体更新或重新发起按钮权限，调用方仍必须校验已发布按钮和数据范围。
     */
    boolean canRestart(String entityCode, String entityRecordId, String previousInstanceId, String userId);
}
