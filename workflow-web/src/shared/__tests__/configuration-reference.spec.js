import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import {
  AUTHORITATIVE_ENUMS,
  CONFIGURATION_SOURCES,
  KNOWN_LIMITATIONS,
  buildConfigurationReference,
  configurationEnumLocation,
  configurationSourceCoverage
} from '../../../scripts/configuration-reference-model.mjs'

const root = process.cwd()
const entries = buildConfigurationReference()
const ids = new Set()

assert.ok(CONFIGURATION_SOURCES.length >= 20, '配置审计应覆盖主要实体、表单、列表和流程设计源')
assert.ok(entries.length >= 250, `逐字段配置目录数量异常，仅发现 ${entries.length} 项`)

for (const entry of entries) {
  assert.ok(entry.id && !ids.has(entry.id), `配置目录 ID 重复: ${entry.id}`)
  ids.add(entry.id)
  for (const key of [
    'domain',
    'area',
    'label',
    'location',
    'binding',
    'meaning',
    'configureWhen',
    'skipWhen',
    'expectedEffect',
    'verification',
    'source'
  ]) {
    assert.ok(String(entry[key] || '').trim(), `${entry.id} 缺少 ${key}`)
  }
  assert.notEqual(entry.example, undefined, `${entry.id} 缺少假设设置`)
  assert.notEqual(entry.example, '示例值', `${entry.id} 仍使用无意义的占位示例`)
  assert.equal(
    ['已配置值', 'DEFAULT', '已配置结构', '示例文案', '已选择项'].includes(entry.example),
    false,
    `${entry.id} 仍使用无业务含义的占位示例`
  )
  assert.notEqual(
    JSON.stringify(entry.example),
    '["VALUE"]',
    `${entry.id} 仍使用无业务含义的集合占位示例`
  )
  assert.notEqual(entry.label, entry.binding, `${entry.id} 标签退化为变量名`)
  assert.match(
    entry.location,
    /.+-.+/,
    `${entry.id} 位置必须使用“菜单域-功能”形式`
  )
  assert.doesNotMatch(
    entry.meaning,
    /在当前配置中的业务参数|设置“.+”对应的配置值|所需的结构化内容或映射规则/,
    `${entry.id} 配置含义过于泛化`
  )
  assert.doesNotMatch(
    entry.expectedEffect,
    /相关场景读取并应用该参数|不同选项会改变处理路径|对应能力；关闭后该能力不参与运行|用于数据转换、调用或展示/,
    `${entry.id} 配置效果过于泛化`
  )
  assert.doesNotMatch(
    entry.configureWhen,
    /^当.+需要明确控制“.+”时配置。$|^需要让“.+”产生以下效果时配置：/,
    `${entry.id} 使用场景仍是循环说明`
  )
  const [file, line] = entry.source.split(':')
  const sourceFile = path.join(root, file)
  assert.equal(existsSync(sourceFile), true, `配置来源文件不存在: ${file}`)
  assert.ok(Number(line) > 0, `配置来源行号无效: ${entry.source}`)
  assert.ok(
    readFileSync(sourceFile, 'utf8').includes(entry.sourceToken || entry.binding),
    `配置绑定已从源码消失但文档未更新: ${entry.source} -> ${entry.sourceToken || entry.binding}`
  )
}

for (const coverage of configurationSourceCoverage()) {
  assert.ok(
    coverage.uniqueIncluded > 0,
    `配置源没有收录任何字段，请检查过滤规则: ${coverage.file}`
  )
  assert.deepEqual(
    coverage.unclassified.map(item => item.binding),
    [],
    `配置源存在既未收录、也未声明为纯界面状态的控件: ${coverage.file}`
  )
}

assert.ok(
  entries.some(entry => entry.binding === 'ccForm.allowManualCc'),
  '用户任务人工知会开关必须可配置并进入逐字段目录'
)

const formLayoutEntry = entries.find(entry =>
  entry.area === '表单定义与初始化'
    && entry.binding === 'form.layoutType'
)
assert.equal(
  formLayoutEntry?.location,
  '实体配置-表单-编辑',
  '表单定义中的布局类型位置应为“实体配置-表单-编辑”'
)
assert.deepEqual(
  entries
    .filter(entry =>
      (entry.domain === '实体配置' && entry.location.startsWith('流程配置-'))
      || (entry.domain === '流程配置' && entry.location.startsWith('实体配置-'))
    )
    .map(entry => `${entry.id} -> ${entry.location}`),
  [],
  '配置位置不能被复用组件的源码路径错误标到另一个配置域'
)

for (const group of AUTHORITATIVE_ENUMS) {
  assert.ok(group.values.length > 0, `权威枚举为空: ${group.area}`)
  assert.match(
    configurationEnumLocation(group),
    /.+-.+/,
    `权威枚举缺少可导航位置: ${group.area}`
  )
  const sourceFile = path.join(root, group.source)
  assert.equal(
    existsSync(sourceFile),
    true,
    `权威枚举来源不存在: ${group.source}`
  )
  const enumSource = readFileSync(sourceFile, 'utf8')
  for (const [value, label, effect] of group.values) {
    assert.ok(String(label || '').trim(), `权威枚举缺少名称: ${group.area} -> ${value}`)
    assert.ok(String(effect || '').trim(), `权威枚举缺少效果: ${group.area} -> ${value}`)
    if (value) {
      assert.ok(
        enumSource.includes(value),
        `权威枚举值已从源码消失但文档未更新: ${group.source} -> ${value}`
      )
    }
  }
}

assert.ok(
  KNOWN_LIMITATIONS.some(item => item.id === 'process.script-task.disabled'),
  '必须明确记录脚本任务当前不可配置'
)
assert.ok(
  KNOWN_LIMITATIONS.some(item => item.id === 'entity.permission.create-by'),
  '必须记录 create_by / created_by 历史问题的当前结论'
)
for (const item of KNOWN_LIMITATIONS) {
  assert.match(item.location || '', /.+-.+/, `受限项缺少位置: ${item.id}`)
}

const generatedDocument = readFileSync(
  path.resolve(root, '../docs/实体与流程配置字段全量说明及验证.md'),
  'utf8'
)
const markdownCell = value => String(value ?? '')
  .replaceAll('|', '\\|')
  .replaceAll('\n', '<br>')
assert.ok(
  generatedDocument.includes('| 设置项 | 位置 | 配置字段 |'),
  '生成文档主配置表缺少“位置”列'
)
assert.ok(
  generatedDocument.includes('| 配置值 | 名称 | 位置 |'),
  '生成文档枚举表缺少“位置”列'
)
assert.ok(
  generatedDocument.includes('| 布局类型 | 实体配置-表单-编辑 | `form.layoutType` |'),
  '生成文档没有写入布局类型的示例位置'
)
for (const entry of entries) {
  assert.ok(
    generatedDocument.includes(
      `| ${markdownCell(entry.label)} | ${markdownCell(entry.location)} | \`${markdownCell(entry.binding)}\` |`
    ),
    `生成文档缺少设置项的位置行: ${entry.id}`
  )
}
for (const group of AUTHORITATIVE_ENUMS) {
  const location = configurationEnumLocation(group)
  for (const [value, label] of group.values) {
    assert.ok(
      generatedDocument.includes(
        `| \`${markdownCell(value)}\` | ${markdownCell(label)} | ${markdownCell(location)} |`
      ),
      `生成文档缺少枚举位置: ${group.area} -> ${value}`
    )
  }
}
for (const item of KNOWN_LIMITATIONS) {
  assert.ok(
    generatedDocument.includes(
      `| ${markdownCell(`${item.domain} / ${item.area}`)} | ${markdownCell(item.setting)} | ${markdownCell(item.location)} |`
    ),
    `生成文档缺少受限项位置: ${item.id}`
  )
}

console.log(`configuration reference audit passed: ${entries.length} fields, ${AUTHORITATIVE_ENUMS.length} enum groups`)
