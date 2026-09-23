package com.workflow.process.sla.runtime.api.response;

import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaPause;

import java.util.List;

/**
 * 任务 SLA 详情响应，主状态、暂停区间和事件记录一起返回供办理页解释计时结果。
 *
 * @param sla 主状态与截止点，供详情显示倒计时
 * @param pauses 暂停区间，解释截止点重算
 * @param events 到期及升级事件，查看执行结果
 */
public record TaskSlaDTO(
        ProcessTaskSla sla,
        List<ProcessTaskSlaPause> pauses,
        List<ProcessTaskSlaEvent> events) {
}
