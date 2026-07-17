-- 历史流程动作会优先回填处理器目录，可能把动作名称或 Bean 名当作中文名称。
-- 仅修正平台内置演示处理器的技术默认值；管理员已经填写的业务名称保持不变。

UPDATE flow_action_definition
SET display_name = '演示：通用流程动作',
    description = CASE
        WHEN description IS NULL
          OR description = ''
          OR description LIKE 'E2E %'
        THEN '开发演示处理器，用于验证通用参数、节点和连线触发；生产环境建议关闭。'
        ELSE description
    END,
    update_time = NOW()
WHERE handler_name = 'demoSimpleActionHandler'
  AND display_name IN (
      'demoSimpleActionHandler',
      'DemoSimpleActionHandler',
      'TRANSITION_TAKEN',
      'PROCESS_STARTED',
      'PROCESS_COMPLETED',
      'PROCESS_WITHDRAWN',
      'PROCESS_TERMINATED',
      'NODE_ENTERED',
      'NODE_COMPLETED',
      'TASK_CREATED',
      'TASK_ASSIGNED',
      'TASK_COMPLETING'
  );

UPDATE flow_action_definition
SET display_name = '演示：类型化流程动作',
    description = CASE
        WHEN description IS NULL
          OR description = ''
          OR description = 'demoTypedActionHandler'
        THEN '类型化参数开发演示，用于验证参数 DTO 映射；生产环境建议关闭。'
        ELSE description
    END,
    update_time = NOW()
WHERE handler_name = 'demoTypedActionHandler'
  AND display_name IN (
      'demoTypedActionHandler',
      'DemoTypedActionHandler'
  );

UPDATE flow_action_definition
SET display_name = '演示：失败与重试动作',
    description = CASE
        WHEN description IS NULL
          OR description = ''
          OR description LIKE 'E2E %'
        THEN '失败、自动重试和死信测试处理器；仅用于测试环境。'
        ELSE description
    END,
    update_time = NOW()
WHERE handler_name = 'demoFailingActionHandler'
  AND display_name IN (
      'demoFailingActionHandler',
      'DemoFailingActionHandler',
      '流程完成后失败动作'
  );
