package com.workflow.contracts.entity.ui.port;

import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import java.util.Map;

/** 将客户端业务定位信息重新解析为服务端已授权的流程上下文，不接受客户端授权结论。 */
public interface UiRuntimeAccessPort {
    /**
     * 核验任务办理或流程只读权限及节点表单绑定。普通页面返回 STANDALONE；
     * 指定任务、实例或节点却无法匹配时必须拒绝，不能回退普通页面权限。
     * @param formId 已由当前发布配置确定的根表单身份
     * @param runtimeContext 不可信定位信息，仅支持 taskId、processInstanceId、nodeId 等业务坐标
     * @return 从实际任务/实例恢复的上下文，供记录校验和只读约束继续使用
     */
    UiRuntimeResolutionContext resolve(String formId, Map<String, Object> runtimeContext);
}
