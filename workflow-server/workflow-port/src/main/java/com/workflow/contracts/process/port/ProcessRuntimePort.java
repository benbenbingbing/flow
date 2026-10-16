package com.workflow.contracts.process.port;

import com.workflow.contracts.process.model.ProcessStartRequest;
import com.workflow.contracts.process.model.ProcessStartResult;

/**
 * 实体绑定流程的内部启动端口；它是完整实体提交编排中的一个步骤。
 * 普通 biz 不应绕过实体准备与授权，仅凭 processKey 直接使用本端口发起业务流程。
 */
public interface ProcessRuntimePort {

    /**
     * 按流程启动请求创建并启动流程实例。
     * 调用方必须已完成业务记录准备、发布按钮及数据范围授权，并在同一事务中锁定业务记录。
     * 启动后还须由原实体编排回写流程关联和运行态；返回并不意味着外层业务事务已经提交。
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
