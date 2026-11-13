package com.workflow.process.instance.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
        List<EntityProcessLink> related = links.selectList(Wrappers.<EntityProcessLink>lambdaQuery()
                .eq(EntityProcessLink::getEntityCode, anchor.getEntityCode())
                .eq(EntityProcessLink::getEntityRecordId, anchor.getEntityRecordId())
                .orderByDesc(EntityProcessLink::getGeneration));
        var ids = related.stream().map(EntityProcessLink::getProcessInstanceId)
                .filter(StringUtils::hasText).collect(java.util.stream.Collectors.toSet());
        var facts = new java.util.HashMap<String, org.flowable.engine.history.HistoricProcessInstance>();
        // 分批限制 IN 参数数；实例事实既用于权限判断，也用于 DTO，不再每轮重复查询实例。
        var orderedIds = new ArrayList<>(ids);
        for (int start = 0; start < orderedIds.size(); start += 200) {
            var batch = new java.util.HashSet<>(orderedIds.subList(start, Math.min(start + 200, orderedIds.size())));
            historyService.createHistoricProcessInstanceQuery().processInstanceIds(batch).list()
                    .forEach(instance -> facts.put(instance.getId(), instance));
        }
        List<ProcessRoundDTO> result = new ArrayList<>();
        for (EntityProcessLink link : related) {
            var historic = facts.get(link.getProcessInstanceId());
            if (historic == null || !accessService.canRead(historic)) continue;
            result.add(round(historic, link.getGeneration() == null ? 1 : link.getGeneration()));
        }
        return result;
    }

    private ProcessRoundDTO round(String instanceId, int generation) {
        var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        if (historic == null) return null;
        return round(historic, generation);
    }

    /** 仅接受本服务从引擎加载且已通过读取范围校验的实例事实。 */
    private ProcessRoundDTO round(org.flowable.engine.history.HistoricProcessInstance historic, int generation) {
        return new ProcessRoundDTO(historic.getId(), generation,
                historic.getEndTime() == null ? "RUNNING" : "COMPLETED",
                historic.getEndTime() == null ? null : ProcessEndReason.category(historic.getDeleteReason()),
                historic.getStartTime(), historic.getEndTime());
    }
}
