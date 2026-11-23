package com.workflow.contracts.process.instance.port;

import java.util.List;

/** 已发布流程动作内的跨实体流程协调。命令及关系路径由当前动作的发布参数提供。 */
public interface RelatedProcessCoordinationPort {
    record Target(String entityCode, String recordId, String processInstanceId, String state) { }

    record Impact(String planId, String operation, List<Target> targets, int active, int terminal, int withoutProcess) {
        public Impact {
            targets = List.copyOf(targets);
        }
    }

    record Result(Impact impact, int queuedTargets) { }

    /** 校验当前动作的协调命令并预览经权限过滤的影响范围。 */
    Impact preview();

    /** 重新校验影响范围，对写操作发布幂等 Outbox 任务，随宿主事务提交。 */
    Result execute();
}
