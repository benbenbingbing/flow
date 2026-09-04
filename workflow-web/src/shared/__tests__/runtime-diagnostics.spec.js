import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  buildRuntimeDiagnosticText,
  createRuntimeDiagnosticTrigger,
  formatRuntimeCodeVersion,
  normalizeRuntimeDiagnosticEntries
} from '../runtime-diagnostics.js'

let timestamp = 0
const trigger = createRuntimeDiagnosticTrigger({ now: () => timestamp })

assert.deepEqual(trigger.recordClick(), { toggled: false, visible: false })
timestamp = 700
assert.deepEqual(trigger.recordClick(), { toggled: false, visible: false })
timestamp = 1499
assert.deepEqual(trigger.recordClick(), { toggled: true, visible: true })

timestamp = 2000
trigger.recordClick()
timestamp = 2100
trigger.recordClick()
timestamp = 2200
assert.deepEqual(
  trigger.recordClick(),
  { toggled: true, visible: false },
  '再次三击应关闭诊断信息'
)

trigger.reset()
timestamp = 3000
trigger.recordClick()
timestamp = 4600
trigger.recordClick()
timestamp = 4700
assert.equal(
  trigger.isVisible(),
  false,
  '超过 1.5 秒的点击不得累计触发'
)

assert.equal(formatRuntimeCodeVersion('order_flow', 12), 'order_flow · v12')
assert.equal(
  formatRuntimeCodeVersion('', null, '未关联流程'),
  '未关联流程 · 版本未记录'
)

assert.deepEqual(
  normalizeRuntimeDiagnosticEntries([
    { label: '流程', value: 'order_flow · v12' },
    { label: '', value: 'ignored' },
    { label: '空值', value: '' }
  ]),
  [{ label: '流程', value: 'order_flow · v12' }]
)

assert.equal(
  buildRuntimeDiagnosticText([
    { label: '列表', value: 'order_list · v6' },
    { label: '流程', value: 'order_flow · v12' },
    { label: '表单', value: 'order_detail · v7' }
  ], '业务数据运行版本'),
  '业务数据运行版本\n列表：order_list · v6\n流程：order_flow · v12\n表单：order_detail · v7'
)

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

const listPage = source('../../views/entity/EntityDataList.vue')
const entityDataTable = source('../../views/entity/components/EntityDataTable.vue')
assert.match(
  listPage,
  /!props\.embedded\s*&&\s*runtimeScene\.value === 'PAGE'/,
  '隐藏入口只能出现在顶层 PAGE 列表，不能进入子列表或弹窗列表'
)
assert.match(
  listPage,
  /listConfig\.value\?\.listKey[\s\S]*listConfig\.value\?\.publishedVersion/,
  '列表诊断必须使用服务端实际解析的列表编码与发布版本'
)
assert.doesNotMatch(
  listPage,
  /entity-data-list__title|listPageTitle/,
  '顶层列表不得为排障入口额外展示列表标题'
)
assert.match(
  listPage,
  /#toolbar-leading[\s\S]*<RuntimeVersionDiagnostics[\s\S]*list-runtime-diagnostics__trigger/,
  '列表排障入口应放在默认列表 tbar 的左侧扩展位'
)
assert.match(
  listPage,
  /showPageRuntimeDiagnostics && \(dataError \|\| usesCustomListComponent\)/,
  '数据错误态和自定义列表仍应保留无标题的排障入口'
)
assert.match(
  entityDataTable,
  /<div class="table-toolbar">[\s\S]*?<slot name="toolbar-leading"\s*\/>\s*<template v-for="btn in toolbarButtons"/,
  '默认列表 tbar 的排障扩展位应与业务按钮保持兄弟关系'
)
assert.match(
  listPage,
  /\.list-runtime-diagnostics\s*\{[\s\S]*?min-width:\s*32px/,
  '窄视口下仍应保留可触发排障信息的最小命中区域'
)

const approvalDialog = source(
  '../../views/entity/components/approval/EntityApprovalDialog.vue'
)
assert.match(
  approvalDialog,
  /<RuntimeVersionDiagnostics[\s\S]*v-if="isViewMode"/,
  '实体弹窗仅允许在查看模式提供隐藏诊断入口'
)
assert.match(
  approvalDialog,
  /runtimeReleaseVersion\s*\?\?\s*form\?\.formReleaseVersion/,
  '查看弹窗应兼容独立表单与流程节点表单的实际运行版本字段'
)
assert.match(
  approvalDialog,
  /hotfixApplied === true \? '（已应用热修复）'/,
  '兼容热修复只能作为基础版本的附加状态展示'
)

const processProgress = source('../../views/ProcessProgress.vue')
assert.match(
  processProgress,
  /<RuntimeVersionDiagnostics[\s\S]*progressData\.processName/,
  '流程进度页应复用既有流程名称作为三击入口'
)
assert.match(
  processProgress,
  /progressData\.value\?\.processVersion/,
  '流程进度页应显示实例实际流程版本'
)

console.log('runtime diagnostics tests passed')
