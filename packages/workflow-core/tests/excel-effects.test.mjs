import test from 'node:test'
import assert from 'node:assert/strict'
import { applyRuntimeEventEffects } from '../src/shared/form-runtime/eventEffects.js'
test('Excel效果在导入结果完成后才继续刷新，未适配宿主明确失败', async () => {
  const calls = []
  const result = { effects: [{ type: 'EXCEL_ACTION', actionContextToken: 'token', operation: 'IMPORT' }, { type: 'REFRESH_LIST' }] }
  await applyRuntimeEventEffects(result, { excel: async effect => { calls.push(effect.actionContextToken) }, refresh: async () => calls.push('refresh') })
  assert.deepEqual(calls, ['token', 'refresh'])
  await assert.rejects(applyRuntimeEventEffects(result, {}), /EXCEL_ACTION/)
})
test('已切换编辑会话不打开Excel文件操作', async () => {
  let called = false
  await applyRuntimeEventEffects({ effects: [{ type: 'EXCEL_ACTION' }] }, { isCurrent: () => false, excel: () => { called = true } })
  assert.equal(called, false)
})
