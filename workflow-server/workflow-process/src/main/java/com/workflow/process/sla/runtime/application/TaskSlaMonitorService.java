package com.workflow.process.sla.runtime.application;

import com.workflow.core.result.PageResult;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** 为 SLA 监控页提供有界分页和状态汇总，不修改任务计时记录。 */
@Service
@RequiredArgsConstructor
public class TaskSlaMonitorService {

    private final ProcessTaskSlaMapper slaMapper;

    /**
     * 按状态、流程、办理人和关键字分页查询；page 从 1 起，size 限制到 1..200，
     * 避免监控页一次查询过多任务。
     *
     * @param requestedPage 请求页码，低于 1 时归一到首页
     * @param requestedSize 请求页大小，限制在 1 到 200 之间
     * @param status 可选 SLA 状态筛选条件
     * @param processKey 可选流程定义键筛选条件
     * @param assignee 可选当前办理人筛选条件
     * @param keyword 可选关键字，传给监控查询
     * @return 包含记录、总数和归一化分页参数的监控结果
     */
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

    /**
     * 返回各状态的记录数，零值也显式返回供监控卡片稳定展示。
     *
     * @return 各状态及其记录数，缺少数据的状态也保留零值
     */
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
