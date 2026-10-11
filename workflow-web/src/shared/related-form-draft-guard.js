// 关联子表单独立保存；宿主关闭/刷新前通过此注册入口检查其草稿和正在进行的提交。
// 不把关联草稿混入主表脏状态基线，避免保存主表后误将未保存的子表单标为已保存。
export const relatedFormDraftGuardKey = Symbol('related-form-draft-guard')
