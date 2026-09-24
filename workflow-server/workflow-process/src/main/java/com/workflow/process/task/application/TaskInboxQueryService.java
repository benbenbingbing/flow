package com.workflow.process.task.application;

import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskInboxMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 工作台的分页读模型；未回填时返回 empty 让调用方保留原有完整读取语义。 */
@Service
@RequiredArgsConstructor
public class TaskInboxQueryService {
    private final TaskInboxMapper mapper;
    private final EntityStatusService statusService;

    @Value("${workflow.task-inbox.read-model-enabled:true}")
    private boolean enabled = true;

    /**
     * 只有用户可见范围的摘要全部就绪才切换。返回 empty 表示回退，不代表列表没有记录。
     * 故障不会伪装为空列表；日期/关键词/总数均由同一数据库条件计算。
     */
    @Transactional(readOnly = true)
    public Optional<PageResult<TaskVO>> findPage(TaskInboxQuery query) {
        if (!enabled || mapper.countUnready(query) > 0) return Optional.empty();
        long total = mapper.count(query);
        Map<String, Map<String, String>> statusNames = new HashMap<>();
        var records = query.getOffset() >= total ? java.util.List.<TaskVO>of()
                : mapper.selectPage(query).stream().map(task -> toVO(task, statusNames)).toList();
        return Optional.of(new PageResult<>(records, total, query.getPageNum(), query.getPageSize()));
    }

    /** 纯摘要映射；状态字典按实体缓存，绝不在逐条映射时读取历史流程或业务详情。 */
    private TaskVO toVO(ProcessTask task, Map<String, Map<String, String>> statusNames) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setTaskName(task.getNodeName());
        vo.setNodeType(task.getNodeType());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setProcessName(task.getProcessName());
        vo.setAssignee(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setAssigneeType(task.getAssigneeType());
        vo.setClaimRequired("group".equalsIgnoreCase(task.getAssigneeType()));
        vo.setCanClaim("todo".equals(task.getStatus()) && "group".equalsIgnoreCase(task.getAssigneeType())
                && !"ADD_SIGN".equals(task.getNodeType()));
        vo.setStartUserName(task.getStartUserName());
        vo.setBusinessKey(task.getBusinessKey());
        vo.setCreateTime(date(task.getStartTime(), ZoneId.systemDefault()));
        vo.setEndTime(date(task.getEndTime(), ZoneId.systemDefault()));
        vo.setDuration(task.getDuration());
        vo.setPriority(task.getPriority());
        vo.setResult(task.getAction());
        vo.setComment(task.getComment());
        vo.setSlaStatus(task.getSlaStatus());
        vo.setResponseDueTime(date(task.getResponseDueTime(), ZoneOffset.UTC));
        vo.setDueTime(date(task.getDueTime(), ZoneOffset.UTC));
        vo.setEntityCode(task.getEntityCode());
        vo.setEntityDataId(task.getEntityDataId());
        vo.setFormKey(task.getFormKey());
        vo.setName(task.getBusinessName());
        vo.setCode(task.getBusinessCode());
        vo.setDataName(task.getBusinessDataName());
        vo.setCurrentTaskName(task.getBusinessCurrentTaskName());
        vo.setEntityStatus(task.getBusinessStatus());
        if (task.getEntityCode() != null && task.getBusinessStatus() != null) {
            vo.setEntityStatusText(statusNames.computeIfAbsent(task.getEntityCode(), statusService::getStatusNameMap)
                    .get(task.getBusinessStatus()));
        }
        return vo;
    }

    private Date date(LocalDateTime value, ZoneId zone) {
        return value == null ? null : Date.from(value.atZone(zone).toInstant());
    }
}
