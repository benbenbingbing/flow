import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { execSync } from 'node:child_process'
import { parse as parseSfc } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'

const files = execSync("rg --files src/views src/components -g '*.vue'", { encoding: 'utf8' })
  .trim()
  .split('\n')
  .filter(Boolean)

const auditedTags = [
  'el-form-item',
  'el-input',
  'el-select',
  'el-date-picker',
  'el-switch',
  'el-radio-group',
  'el-checkbox-group',
  'el-table-column',
  'el-dialog',
  'el-button',
  'el-upload',
  'el-tree',
  'el-tree-select',
  'el-cascader',
  'el-tabs',
  'el-tab-pane'
]

const counts = Object.fromEntries(auditedTags.map((tag) => [tag, 0]))
const issues = []

function attrs(node) {
  const map = new Map()
  for (const prop of node.props || []) {
    if (prop.type === NodeTypes.ATTRIBUTE) {
      map.set(prop.name, prop.value?.content ?? true)
    } else if (prop.type === NodeTypes.DIRECTIVE) {
      map.set(`${prop.name}:${prop.arg?.content || ''}`, prop.exp?.content || true)
    }
  }
  return map
}

function hasAny(attrMap, keys) {
  return keys.some((key) => attrMap.has(key))
}

function textContent(node) {
  let text = ''
  for (const child of node.children || []) {
    if (child.type === NodeTypes.TEXT) text += child.content.trim()
    if (child.type === NodeTypes.INTERPOLATION) text += '{}'
    if (child.type === NodeTypes.ELEMENT) text += textContent(child)
  }
  return text
}

function recordIssue(file, tag, message) {
  issues.push(`${file}: ${tag} ${message}`)
}

function auditNode(node, file) {
  if (node.type === NodeTypes.ELEMENT) {
    const tag = node.tag
    const attrMap = attrs(node)
    const text = textContent(node)

    if (counts[tag] !== undefined) counts[tag]++

    if (tag === 'el-form-item' && !hasAny(attrMap, ['label', 'bind:label', 'prop', 'bind:prop']) && text === '') {
      recordIssue(file, tag, '缺少 label/prop 且无可见内容')
    }

    if (
      ['el-input', 'el-select', 'el-date-picker', 'el-switch', 'el-radio-group', 'el-checkbox-group', 'el-tree-select', 'el-cascader'].includes(tag) &&
      !hasAny(attrMap, ['model:', 'bind:modelValue', 'bind:model-value', 'model-value', 'bind:', 'v-bind'])
    ) {
      recordIssue(file, tag, '缺少 v-model/model-value')
    }

    if (tag === 'el-table-column' && !hasAny(attrMap, ['label', 'bind:label', 'type', 'prop', 'bind:prop']) && text === '') {
      recordIssue(file, tag, '缺少 label/prop/type 且无可见内容')
    }

    if (tag === 'el-dialog' && !hasAny(attrMap, ['title', 'bind:title']) && !text.includes('header')) {
      recordIssue(file, tag, '缺少 title 或 header 插槽')
    }

    if (tag === 'el-tab-pane' && !hasAny(attrMap, ['label', 'bind:label', 'name', 'bind:name'])) {
      recordIssue(file, tag, '缺少 label/name')
    }

    if (tag === 'el-button' && !hasAny(attrMap, ['on:click', 'nativeOn:click', 'type', 'native-type']) && text === '') {
      recordIssue(file, tag, '缺少点击/类型/可见内容')
    }
  }

  for (const child of node.children || []) auditNode(child, file)
}

for (const file of files) {
  const source = readFileSync(file, 'utf8')
  const { descriptor } = parseSfc(source, { filename: file })
  if (!descriptor.template) continue
  const ast = baseParse(descriptor.template.content)
  auditNode(ast, file)
}

assert.equal(issues.length, 0, `UI 配置审计失败:\n${issues.join('\n')}`)
console.log(`ui configuration audit passed: ${files.length} files, ${Object.values(counts).reduce((sum, count) => sum + count, 0)} controls`)
