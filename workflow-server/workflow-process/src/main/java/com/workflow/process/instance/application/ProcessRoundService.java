package com.workflow.process.instance.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.instance.api.response.ProcessRoundDTO;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.status.application.ProcessEndReason;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;

/** 按持久化的代次关联列出各轮历史；知道某一轮 ID 不自动授予其他轮次的访问权。 */
@Service
@RequiredArgsConstructor
public class ProcessRoundService {
    private final EntityProcessLinkMapper links;
    private final HistoryService historyService;
    private final ProcessInstanceAccessService accessService;

    /** 校验入口及每一轮的读取权限，跳过无权限轮次；不存在关联的旧实例仍可单独查看。 */
    public List<ProcessRoundDTO> listReadableRounds(String instanceId) {
        accessService.requireReadAccess(instanceId);
        EntityProcessLink anchor = links.findByProcessInstanceId(instanceId);
        if (anchor == null) {
            ProcessRoundDTO legacy = round(instanceId, 1);
            return legacy == null ? List.of() : List.of(legacy);
        }
        List<ProcessRoundDTO> result = new ArrayList<>();
        for (EntityProcessLink link : links.selectList(Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getEntityCode, anchor.getEntityCode())
                .eq(EntityProcessLink::getEntityRecordId, anchor.getEntityRecordId())
                .orderByDesc(EntityProcessLink::getGeneration))) {
            if (!StringUtils.hasText(link.getProcessInstanceId())) continue;
            try {
                accessService.requireReadAccess(link.getProcessInstanceId());
            } catch (ForbiddenException denied) {
                continue;
            }
            ProcessRoundDTO round = round(link.getProcessInstanceId(),
                    link.getGeneration() == null ? 1 : link.getGeneration());
            if (round != null) result.add(round);
        }
        return result;
    }

    private ProcessRoundDTO round(String instanceId, int generation) {
        var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        if (historic == null) return null;
        return new ProcessRoundDTO(instanceId, generation,
                historic.getEndTime() == null ? "RUNNING" : "COMPLETED",
                historic.getEndTime() == null ? null : ProcessEndReason.category(historic.getDeleteReason()),
                historic.getStartTime(), historic.getEndTime());
    }
}
