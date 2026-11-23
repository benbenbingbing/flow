package com.workflow.contracts.process.instance.port;

import com.workflow.contracts.process.instance.model.ProcessRuntimeViews.InstanceCapabilities;
import com.workflow.contracts.process.instance.model.ProcessRuntimeViews.Progress;
import com.workflow.contracts.process.query.model.ActionCapability;

/** 实例进度与干预入口的只读查询，由宿主按当前用户授权。 */
public interface ProcessInstanceInspectionPort {
    /** 查看实际运行轮次的节点与已执行连线。 */
    Progress readProgress(String processInstanceId);

    /** 查询撤回、终止及接收任务的当前可执行性；执行命令仍需重新校验。 */
    InstanceCapabilities capabilities(String processInstanceId);

    /** 验证指定的接收分支和消息标识；并行分支可通过 executionId 精确定位。 */
    ActionCapability previewReceive(String processInstanceId, String executionId, String activityId, String messageRef);
}
