import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import {
  AUTHORITATIVE_ENUMS,
  KNOWN_LIMITATIONS,
  buildConfigurationReference,
  configurationEnumLocation
} from './configuration-reference-model.mjs'
import { CONFIG_FIELD_HELP } from '../src/shared/config-field-help.js'

const root = process.cwd()
const output = path.resolve(root, '../docs/实体与流程配置字段全量说明及验证.md')
const entries = buildConfigurationReference()

const escapeCell = value => String(value ?? '')
  .replaceAll('|', '\\|')
  .replaceAll('\n', '<br>')

const printable = value => {
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

const lines = [
  '# 实体与流程配置字段全量说明及验证',
  '',
  '> 生成日期：2026-08-01。本文由设计器源码、权威配置 schema 和验证清单生成；请勿直接手工修改生成表格。',
  '',
  '## 阅读说明',
  '',
  '- “什么时候配置”说明需要主动改动平台默认约定的场景；“什么时候不用”用于避免无效或过度配置。',
  '- “位置”按“菜单域-功能-页面或抽屉-配置区”描述实际入口；带“或”的路径表示同一通用配置器可从多个设计器进入。',
  '- 每项都给出一个假设值和预期效果。验证证据分为前端单元测试、后端运行时测试、静态保存闭环和实际构建。',
  '- 设计器只在含义不直观、风险较高或容易误配的字段旁显示问号帮助；名称、编码、启用等可直接理解的字段不重复堆叠提示。',
  '- 纯界面状态，例如抽屉开关、查询分页、预览尺寸和当前页签，不属于发布配置，因此不进入字段清单。',
  '- 最终运行效果以发布版本为准；草稿修改未发布时，只影响设计预览。',
  '',
  `## 覆盖摘要`,
  '',
  `- 可持久化设计控件：${entries.length} 项`,
  `- 权威枚举：${AUTHORITATIVE_ENUMS.reduce((sum, group) => sum + group.values.length, 0)} 项`,
  `- 复杂配置问号帮助：${Object.keys(CONFIG_FIELD_HELP).length} 项`,
  `- 已知限制与历史问题：${KNOWN_LIMITATIONS.length} 项`,
  '',
  '## 本次审查结论',
  '',
  '- 已修复：用户任务的 `ccForm.allowManualCc` 原来能够保存且后端会执行，但设计器没有配置入口；现已在“知会配置”中增加“允许手工知会”开关，并增加后端运行时测试。',
  '- 已纠正：实体生命周期和办理人方式的示例值改为真实枚举 `WORKFLOW`、`role`，避免文档示例无法被配置接受。',
  '- 已确认：动态业务表的创建人字段使用 `create_by`；旧配置中的 `created_by` 由后端兼容归一化。',
  '- 已确认：节点自动跳过按运行时 `ACTIVITY_STARTED` 的实际令牌到达处理，不再通过首任务遍历推断。',
  '- 仍受限：脚本任务未开放；旧字段脚本事件只用于受信任历史配置；系统实体不开放写操作和动作插槽。',
  '',
  '## 2026-08-01 验证结果',
  '',
  `- 配置目录审计通过：${entries.length} 个字段、${AUTHORITATIVE_ENUMS.length} 个关键枚举组均能回溯到当前源码，且没有未分类的持久化 \`v-model\` 控件。`,
  `- 配置帮助审计通过：${Object.keys(CONFIG_FIELD_HELP).length} 个复杂配置点都有问号说明，直观字段不重复展示帮助图标。`,
  '- 浏览器验收通过：实体权限、列表访问范围、表单按钮和用户任务知会页面布局正常；“允许手工知会”入口可见，页面控制台无错误。',
  '- 前端配置、集成、功能、页面、术语和生产构建验证通过；完整 `npm test` 最后仅被现有的 21 项文件行数预算阻止。',
  '- 后端聚焦验证通过：实体配置相关 36 个测试、流程与运行时相关 79 个测试，共 115 个测试无失败。',
  '- 后端全量 Maven Reactor 在 `workflow-open-api` 的 3 个 Testcontainers 数据库用例处停止，原因是本机未提供 Docker；已经执行的测试没有断言失败。',
  ''
]

for (const domain of ['实体配置', '流程配置']) {
  lines.push(`## ${domain}`, '')
  const domainEntries = entries.filter(entry => entry.domain === domain)
  const areas = [...new Set(domainEntries.map(entry => entry.area))]
  for (const area of areas) {
    const areaEntries = domainEntries.filter(entry => entry.area === area)
    lines.push(`### ${area}`, '')
    lines.push('| 设置项 | 位置 | 配置字段 | 配置含义 | 什么时候配置 | 什么时候不用 | 假设设置 | 配置后的预期效果 | 验证证据 | 源码 |')
    lines.push('|---|---|---|---|---|---|---|---|---|---|')
    for (const entry of areaEntries) {
      lines.push(`| ${[
        entry.label,
        entry.location,
        `\`${entry.binding}\``,
        entry.meaning,
        entry.configureWhen,
        entry.skipWhen,
        `\`${printable(entry.example)}\``,
        entry.expectedEffect,
        entry.verification,
        `\`${entry.source}\``
      ].map(escapeCell).join(' | ')} |`)
    }
    lines.push('')
  }
}

lines.push('## 类型与节点枚举', '')
for (const group of AUTHORITATIVE_ENUMS) {
  lines.push(`### ${group.area}`, '')
  lines.push(
    `来源：\`${group.source}\`。验证：配置目录审计会逐值确认这些枚举仍存在于源码。`,
    ''
  )
  lines.push('| 配置值 | 名称 | 位置 | 什么时候使用 / 效果 |')
  lines.push('|---|---|---|---|')
  for (const [value, label, effect] of group.values) {
    lines.push(`| \`${escapeCell(value)}\` | ${escapeCell(label)} | ${escapeCell(configurationEnumLocation(group))} | ${escapeCell(effect)} |`)
  }
  lines.push('')
}

lines.push('## 当前不可用、受限与历史问题', '')
lines.push('| 配置域 | 设置项 | 位置 | 状态 | 原因 | 建议 |')
lines.push('|---|---|---|---|---|---|')
for (const item of KNOWN_LIMITATIONS) {
  lines.push(`| ${[
    `${item.domain} / ${item.area}`,
    item.setting,
    item.location,
    item.status,
    item.reason,
    item.recommendation
  ].map(escapeCell).join(' | ')} |`)
}
lines.push('')

lines.push('## 验证口径', '')
lines.push('1. **字段存在性**：覆盖测试从 Vue 模板读取真实 `v-model`，确认文档字段仍有对应控件。')
lines.push('2. **保存闭环**：配置对象必须进入现有保存或发布对象；纯弹窗状态不会被误列为业务配置。')
lines.push('3. **规则行为**：字段校验、列表保存、表单节点、回填、条件组、权限 SQL、流程动作等使用现有前后端测试验证。')
lines.push('4. **构建验证**：前端生产构建验证所有配置页面可编译。')
lines.push('5. **运行验证**：高风险组合还需在真实服务上做发布、回滚、重新选择、权限不足、分支流转、SLA 超时等场景验收。')
lines.push('')

mkdirSync(path.dirname(output), { recursive: true })
writeFileSync(output, `${lines.join('\n')}\n`, 'utf8')
console.log(`generated ${path.relative(root, output)} with ${entries.length} configuration entries`)
