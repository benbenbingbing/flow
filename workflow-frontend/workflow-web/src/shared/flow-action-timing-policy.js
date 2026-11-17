/** 后端声明的前置阶段必须同步阻断，处理器推荐配置不能覆盖这一约束。 */
export function timingAllowsMode(timing, mode) {
  return !timing?.blocking || mode === 'IN_TRANSACTION'
}

export function timingAllowsFailurePolicy(timing, policy) {
  return !timing?.blocking || policy === 'ROLLBACK'
}

/** 切换到前置时机时清除原有 CUSTOM/CONTINUE，避免表单保留失效策略。 */
export function timingDefaults(timing, previousPolicy) {
  return {
    executionMode: timing.defaultExecutionMode,
    failurePolicy: timing.blocking || previousPolicy !== 'CUSTOM'
      ? timing.defaultFailurePolicy : previousPolicy
  }
}
