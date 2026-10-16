import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { stripTypeScriptTypes } from 'node:module'
import { babelParse, parse } from '@vue/compiler-sfc'
import { reactive, ref } from 'vue'

const source = await readFile(new URL('../Role.vue', import.meta.url), 'utf8')
const script = parse(source).descriptor.scriptSetup.content
const ast = babelParse(script, { sourceType: 'module', plugins: ['typescript'] })
const submitNode = ast.program.body.find(node => node.type === 'VariableDeclaration'
  && node.declarations.some(declaration => declaration.id.name === 'handleSubmit'))
assert.ok(submitNode, '无法定位角色页面的实际提交方法')
const submitCode = stripTypeScriptTypes(script.slice(submitNode.start, submitNode.end))

const apiSource = stripTypeScriptTypes(await readFile(
  new URL('../../../api/system/role.ts', import.meta.url), 'utf8'
))
const apiAst = babelParse(apiSource, { sourceType: 'module' })
const apiCode = apiAst.program.body.filter(node => node.type !== 'ImportDeclaration')
  .map(node => node.type === 'ExportNamedDeclaration' ? node.declaration : node)
  .map(node => apiSource.slice(node.start, node.end)).join('\n')

/** 执行真实页面方法与角色 API，只替换请求传输，防止不同接口参数签名被测试替身掩盖。 */
function createPage({ id = '', validate = async () => true, requestError } = {}) {
  const requests = []
  const messages = []
  let refreshes = 0
  const formData = reactive({ id, roleName: '1', roleCode: '1', description: '', sort: 0, status: '0' })
  const submitLoading = ref(false)
  const dialogVisible = ref(true)
  const request = {
    post: async (url, data) => {
      assert.equal(submitLoading.value, true, '发送请求期间应保持提交状态')
      requests.push({ url, data })
      if (requestError) throw requestError
      return { ...data, id: id || 'created-role' }
    }
  }
  const roleApi = new Function('request', `${apiCode}\nreturn { createRole, updateRole }`)(request)
  const dependencies = {
    ...roleApi,
    formData,
    formRef: ref({ validate }),
    submitLoading,
    dialogVisible,
    ElMessage: { success: message => messages.push(message) },
    fetchRoleList: () => { refreshes++ }
  }
  const submit = new Function(...Object.keys(dependencies), `${submitCode}\nreturn handleSubmit`)(
    ...Object.values(dependencies)
  )
  return { submit, formData, submitLoading, dialogVisible, requests, messages, get refreshes() { return refreshes } }
}

for (const id of ['', 'role-123']) {
  const page = createPage({ id })
  const expectedForm = { ...page.formData }
  await page.submit()
  assert.deepEqual(page.requests, [{
    url: id ? '/system/role/role-123/update' : '/system/role',
    data: expectedForm
  }], id ? '编辑角色应将 ID 用于路径并发送完整表单' : '新增角色空 ID 不能取代完整请求体')
  assert.deepEqual(page.messages, [id ? '更新成功' : '创建成功'])
  assert.equal(page.dialogVisible.value, false, '保存成功后关闭弹窗')
  assert.equal(page.refreshes, 1, '保存成功后刷新角色列表')
  assert.equal(page.submitLoading.value, false)
}

for (const id of ['', 'role-123']) {
  const failure = new Error('角色编码已存在')
  const page = createPage({ id, requestError: failure })
  const expectedForm = { ...page.formData }
  await assert.rejects(page.submit(), error => error === failure)
  assert.equal(page.requests.length, 1)
  assert.equal(page.dialogVisible.value, true, '保存失败后保留弹窗供用户修改重试')
  assert.deepEqual({ ...page.formData }, expectedForm, '保存失败不能丢失用户输入')
  assert.deepEqual(page.messages, [], '保存失败不能提示成功')
  assert.equal(page.refreshes, 0, '保存失败不能触发成功刷新')
  assert.equal(page.submitLoading.value, false, '保存失败后应恢复提交状态')
}

const invalid = createPage({ validate: async () => { throw new Error('角色名称不能为空') } })
await assert.rejects(invalid.submit(), /角色名称不能为空/)
assert.deepEqual(invalid.requests, [], '表单校验失败不能发送请求')
assert.deepEqual(invalid.messages, [])
assert.equal(invalid.refreshes, 0)
assert.equal(invalid.dialogVisible.value, true)
assert.equal(invalid.submitLoading.value, false)

console.log('role management submission tests passed')
