package com.workflow.admin.identity.position.application;

import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 定时对账 UNIT_LEADER 权威任职与旧 leader 字段兼容投影。
 *
 * <p>预约转任在生效时没有管理写请求可触发投影，因此必须由对账任务刷新；
 * 锁顺序仍为职务后组织，与任职写事务一致。</p>
 */
@Service
@RequiredArgsConstructor
public class PositionLeaderProjectionReconciler {

    private final SysPositionMapper positionMapper;
    private final SysPositionAssignmentMapper assignmentMapper;
    private final SysOrganizationMapper organizationMapper;
    private final PositionAssignmentService assignmentService;

    @Scheduled(fixedDelayString =
            "${workflow.position.leader-projection-reconcile-ms:60000}")
    @Transactional(rollbackFor = Exception.class)
    public void reconcile() {
        SysPosition position = positionMapper.selectByCode(
                PositionAssignmentService.UNIT_LEADER);
        if (position == null) {
            return;
        }
        positionMapper.selectForUpdate(position.getId());
        List<String> unitIds = assignmentMapper.selectLeaderProjectionUnitIds();
        if (unitIds.isEmpty()) {
            return;
        }
        organizationMapper.selectForUpdateByIds(unitIds);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        unitIds.forEach(unitId ->
                assignmentService.refreshLeaderProjection(unitId, now));
    }
}
