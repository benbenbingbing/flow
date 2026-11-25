package com.workflow.port.process;

import com.workflow.port.process.model.ProcessStartRequest;
import com.workflow.port.process.model.ProcessStartResult;

/**
 * 实体绑定流程的内部启动端口；它是完整实体提交编排中的一个步骤。
 * 普通 biz 不应绕过实体准备与授权，仅凭 processKey 直接使用本端口发起业务流程。
 */
public interface EntityBoundProcessPort {

    /**
     * 写操作。按流程启动请求创建并启动流程实例。
     * 调用方必须已完成业务记录准备、发布按钮及数据范围授权，并在同一事务中锁定业务记录。
     * 启动后还须由原实体编排回写流程关联和运行态；返回并不意味着外层业务事务已经提交。
     *
     * @param request 流程启动请求
     * @return 启动结果
     */
    ProcessStartResult start(ProcessStartRequest request);

    /**
     * 读操作。校验旧实例确实是该记录最新一轮已撤回的流程。发起时会在完成加锁后重新校验。
     */
    boolean canRestartLatest(String entityCode, String entityRecordId, String previousInstanceId);

    /**
     * 读操作。判断最新一轮已撤回实例是否由指定用户发起，供页面检查发起人资格。
     */
    boolean canRestart(String entityCode, String entityRecordId, String previousInstanceId, String userId);
}
