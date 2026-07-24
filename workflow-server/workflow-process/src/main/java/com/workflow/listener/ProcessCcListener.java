package com.workflow.listener;

import com.workflow.entity.ProcessCcRecord;
import com.workflow.service.ProcessCcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程抄送监听器
 * 在流程节点执行时自动触发抄送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCcListener implements ExecutionListener {
    
    /** 抄送服务，创建抄送记录 */
    private final ProcessCcService ccService;

    /**
     * 节点执行事件回调：从流程变量读取抄送人列表并逐人生成抄送记录。
     *
     * @param execution Flowable 执行上下文
     */
    @Override
    public void notify(DelegateExecution execution) {
        String eventName = execution.getEventName();
        String processInstanceId = execution.getProcessInstanceId();
        String processDefinitionId = execution.getProcessDefinitionId();
        
        log.debug("抄送监听器触发: event={}, processInstanceId={}", eventName, processInstanceId);
        
        // 从流程变量中获取抄送人列表
        @SuppressWarnings("unchecked")
        List<String> ccUsers = (List<String>) execution.getVariable("_ccUsers_");
        
        if (ccUsers == null || ccUsers.isEmpty()) {
            return;
        }
        
        // 创建抄送记录
        for (String userId : ccUsers) {
            ProcessCcRecord record = new ProcessCcRecord();
            record.setProcessInstanceId(processInstanceId);
            record.setProcessDefinitionId(processDefinitionId);
            // 从流程定义ID中提取key（简化处理）
            record.setProcessKey(extractProcessKey(processDefinitionId));
            record.setNodeId(execution.getCurrentActivityId());
            // 节点名称需要通过其他方式获取
            record.setNodeName(getNodeName(execution));
            record.setCcUserId(userId);
            record.setCcType("AUTO");
            record.setCcTiming(mapEventToTiming(eventName));
            
            ccService.createCcRecord(record);
        }
        
        log.info("流程 {} 自动抄送给 {} 人", processInstanceId, ccUsers.size());
    }
    
    /**
     * 将事件名称映射为抄送时机
     */
    private String mapEventToTiming(String eventName) {
        switch (eventName) {
            case "start": return "START";
            case "end": return "COMPLETE";
            case "take": return "APPROVE";
            default: return "OTHER";
        }
    }
    
    /**
     * 从流程定义ID提取processKey
     */
    private String extractProcessKey(String processDefinitionId) {
        // 流程定义ID格式: processKey:version:deploymentId
        if (processDefinitionId != null && processDefinitionId.contains(":")) {
            return processDefinitionId.split(":")[0];
        }
        return processDefinitionId;
    }
    
    /**
     * 获取节点名称
     */
    private String getNodeName(DelegateExecution execution) {
        // 尝试从变量中获取，或通过当前活动ID获取
        String activityId = execution.getCurrentActivityId();
        return activityId != null ? activityId : "";
    }
}
