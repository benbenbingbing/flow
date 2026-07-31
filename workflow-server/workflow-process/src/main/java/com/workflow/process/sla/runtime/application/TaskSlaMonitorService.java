package com.workflow.process.sla.runtime.application;

import com.workflow.core.result.PageResult;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskSlaMonitorService {

    private final ProcessTaskSlaMapper slaMapper;

    @Transactional(readOnly = true)
    public PageResult<ProcessTaskSla> page(
            int requestedPage,
            int requestedSize,
            String status,
            String processKey,
            String assignee,
            String keyword) {
        int page = Math.max(1, requestedPage);
        int size = Math.min(200, Math.max(1, requestedSize));
        long offset = (long) (page - 1) * size;
        return new PageResult<>(
                slaMapper.findMonitorPage(
                        status,
                        processKey,
                        assignee,
                        keyword,
                        offset,
                        size),
                slaMapper.countMonitor(
                        status,
                        processKey,
                        assignee,
                        keyword),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> statistics() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("RUNNING", 0L);
        result.put("BREACHED", 0L);
        result.put("PAUSED", 0L);
        result.put("COMPLETED", 0L);
        for (Map<String, Object> row : slaMapper.statusStatistics()) {
            result.put(
                    String.valueOf(row.get("status")),
                    ((Number) row.get("total")).longValue());
        }
        return result;
    }
}
