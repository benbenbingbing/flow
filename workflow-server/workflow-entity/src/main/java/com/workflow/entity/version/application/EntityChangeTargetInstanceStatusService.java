package com.workflow.entity.version.application;

import com.workflow.entity.version.infrastructure.persistence.mapper.EntityChangeTargetInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 独立记录变更目标生效状态，业务批次回滚时仍保留失败信息。
 */
@Service
@RequiredArgsConstructor
public class EntityChangeTargetInstanceStatusService {

    private final EntityChangeTargetInstanceMapper instanceMapper;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void update(
            List<String> instanceIds,
            String status) {
        for (String instanceId : instanceIds) {
            instanceMapper.updateStatus(
                    instanceId,
                    status);
        }
    }
}
