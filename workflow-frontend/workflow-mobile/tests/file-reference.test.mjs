import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
import * as attachments from '../../packages/workflow-core/src/shared/file-attachment.js'

/** 执行真实移动附件组件 setup，验证业务引用在上传、列表和删除之间不丢失原名。 */
function mount(props) {
  const { descriptor } = parse(readFileSync(new URL('../../packages/workflow-mobile-ui/src/fields/MobileFileField.vue', import.meta.url), 'utf8'))
  compileScript(descriptor, { id: 'file-reference-test' })
  const source = descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const bindings = { ...vue, ...attachments, defineProps: () => props, defineExpose() {},
    defineEmits: () => (event, value) => { if (event === 'update:modelValue') props.modelValue = value },
    safeParseConfig: value => value || {} }
  return new Function(...Object.keys(bindings), `${source}\nreturn { upload, remove, retry, fileList, failed }`)(...Object.values(bindings))
}

test('移动端附件保留 fileId、原名与大小，上传失败重试不重复已完成文件', async () => {
  let fail = true
  const calls = []
  const props = vue.reactive({ field: { fieldType: 'FILE', fileMaxCount: 3 }, modelValue: [], context: {},
    services: { async uploadFile(file) {
      calls.push(file.name)
      if (file.name === 'two.pdf' && fail) throw new Error('上传失败')
      return { fileId: file.name.replace('.', '-'), originalName: file.name, size: 12 }
    } } })
  const state = mount(props)
  await state.upload([{ file: { name: 'one.pdf' } }, { file: { name: 'two.pdf' } }], {}, 0)
  assert.deepEqual(props.modelValue, [{ fileId: 'one-pdf', name: 'one.pdf', size: 12 }])
  fail = false
  await state.retry()
  // retry 是事件入口；等待其内部异步上传结束。
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.deepEqual(calls, ['one.pdf', 'two.pdf', 'two.pdf'])
  assert.deepEqual(props.modelValue.map(file => file.fileId), ['one-pdf', 'two-pdf'])
  assert.equal(state.fileList({}, 0)[0].file.name, 'one.pdf')
  assert.equal(state.fileList({}, 0)[0].url, undefined)
  state.remove({}, 0, 0)
  assert.deepEqual(props.modelValue, [{ fileId: 'two-pdf', name: 'two.pdf', size: 12 }])
})
