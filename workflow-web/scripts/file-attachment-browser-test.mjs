import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createServer } from 'vite'

// 挂载真实表单与附件控件；上传、保存和刷新均使用隔离数据，不接触业务文件或数据库。
const harness = `
import { createApp, h, ref, reactive, nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Preview from '/src/components/FormPreviewLinkage.vue'
import FileField from '/src/extensions/builtin/fields/components/FileField.vue'
import { registerFormFieldComponent } from '/src/extensions/core/registries/formFieldRegistry.js'
import { fileApi } from '/src/api/file.js'
import request from '/src/utils/request.js'
request.defaults.adapter=async config=>({data:{code:200,data:[]},status:200,statusText:'OK',headers:{},config})
registerFormFieldComponent('file',FileField,{origin:'PLATFORM'})
const copy=v=>JSON.parse(JSON.stringify(v))
const initial={attachment:{旧需求说明书:[{url:'/uploads/old.pdf',name:'old.pdf'}],设计说明书:[{url:'/uploads/design.pdf',name:'design.pdf'}]},single:[{url:'/uploads/first.pdf',name:'first.pdf'},'/uploads/second.pdf']}
const state=reactive({data:JSON.parse(localStorage.getItem('file-attachment-test')||'null')||copy(initial),uploads:[],fail:false,delay:0,readonly:false})
fileApi.upload=async file=>{state.uploads.push(file.name);await new Promise(r=>setTimeout(r,state.delay));if(state.fail)throw Error('模拟上传失败');return {url:'/uploads/'+file.name,originalName:file.name}}
const items=[{itemKey:'requirements',itemName:'需求说明书',nameAliases:['旧需求说明书'],required:true,fileTypes:'.pdf',maxCount:1,maxSize:1},{itemKey:'design',itemName:'设计说明书',fileTypes:'.pdf',maxCount:2,maxSize:1}]
const fields=[{id:'attachment',fieldCode:'attachment',fieldName:'我是文件',fieldType:'FILE',componentType:'file',fileItems:items},{id:'single',fieldCode:'single',fieldName:'单组附件',fieldType:'FILE',componentType:'file',fileMaxCount:3}]
const nodes=[{id:'section',nodeKey:'section',nodeType:'SECTION',props:{label:'附件回归'}},...fields.map((f,i)=>({id:'node-'+f.id,parentId:'section',nodeType:'FIELD',nodeKey:f.fieldCode,bindingType:'ENTITY_FIELD',bindingRef:f.fieldCode,orderKey:i+1,props:{...f,label:f.fieldName}}))]
const form={id:'attachment-form',formName:'附件回归',layoutType:'grid',fields,nodes,viewConfig:{}}
const preview=ref(null)
createApp({setup(){return()=>h('main',{style:'width:800px;margin:24px auto'},[
 h(Preview,{ref:preview,form,mode:'edit',showHeader:false,designPreview:true,readonly:state.readonly,modelValue:state.data,'onUpdate:modelValue':value=>state.data=value}),
 h('button',{id:'save',onClick:()=>localStorage.setItem('file-attachment-test',JSON.stringify(state.data))},'保存')
])}}).use(createPinia()).use(ElementPlus).mount('#app')
window.attachmentTest={state,initial,items,preview,copy,settle:async()=>{await nextTick();await new Promise(r=>setTimeout(r,150));await nextTick()}}
`

const fixture = mkdtempSync(path.resolve('.file-attachment-fixture-'))
const profile = mkdtempSync(path.join(tmpdir(), 'file-attachment-chrome-'))
const uploadDir = mkdtempSync(path.join(tmpdir(), 'file-attachment-files-'))
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
for (const name of ['replacement.pdf','new.pdf','bad.txt','failed.pdf','late.pdf','requirements.pdf','design-new.pdf']) writeFileSync(path.join(uploadDir,name),name)
writeFileSync(path.join(uploadDir,'large.pdf'),Buffer.alloc(1024*1024+1))
let server, browser, ws, nextId = 0
const pending = new Map(), errors = [], choosers = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

async function send(method, params = {}) {
  const id = ++nextId
  const promise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)) }, 15000)
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result) }, reject: error => { clearTimeout(timer); reject(error) } })
  })
  ws.send(JSON.stringify({ id, method, params }))
  return promise
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true })
  if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}
async function waitFor(expression, message) {
  for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await sleep(100) }
  assert.fail(message + ': ' + errors.join('\n'))
}
async function snapshot(expression) {
  return JSON.parse(await evaluate(`JSON.stringify(${expression})`))
}
async function click(label, uploaderIndex = 0, matchIndex = 0) {
  await evaluate(`(() => {const root=document.querySelectorAll('.file-uploader')[${uploaderIndex}],button=[...root.querySelectorAll('button')].filter(e=>e.textContent.trim()===${JSON.stringify(label)})[${matchIndex}];if(!button)throw Error('Missing button: '+${JSON.stringify(label)});button.click()})()`)
  await evaluate('attachmentTest.settle()')
}
async function setFile(name, uploaderIndex = 0, backendNodeId = null) {
  if (!backendNodeId) {
    const { root } = await send('DOM.getDocument', { depth: -1 })
    const { nodeIds } = await send('DOM.querySelectorAll', { nodeId: root.nodeId, selector: '.file-uploader input.el-upload__input' })
    await send('DOM.setFileInputFiles', { nodeId: nodeIds[uploaderIndex], files: [path.join(uploadDir,name)] })
  } else await send('DOM.setFileInputFiles', { backendNodeId, files: [path.join(uploadDir,name)] })
  await evaluate('attachmentTest.settle()')
}
async function replace(name, fileIndex = 0) {
  const count=choosers.length
  await click('更新',0,fileIndex)
  assert.equal(choosers.length,count+1,'更新必须打开文件选择器')
  await setFile(name,0,choosers.at(-1).backendNodeId)
}
async function saveAndReload() {
  const data=await snapshot('attachmentTest.state.data')
  await evaluate("document.querySelector('#save').click();window.attachmentTest=null")
  await send('Page.reload')
  await waitFor("Boolean(window.attachmentTest)&&document.querySelectorAll('.file-uploader').length===2",'Refresh did not initialize')
  assert.deepEqual(await snapshot('attachmentTest.state.data'),data,'刷新必须保留保存的附件修改')
}
try {
  server = await createServer({ cacheDir: path.join(fixture,'cache'), optimizeDeps: { include: ['axios'], entries: [path.join(fixture,'index.html')] }, server: { host: '127.0.0.1', port: 3400, strictPort: true } })
  await server.listen()
  browser = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=9400',`--user-data-dir=${profile}`,'about:blank'], { stdio: 'ignore' })
  let targets
  for(let i=0;i<100;i++){try{targets=await(await fetch('http://127.0.0.1:9400/json/list')).json();break}catch{await sleep(100)}}
  assert.ok(targets?.length,'Chrome failed to start')
  ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl)
  ws.addEventListener('message',event=>{
    const message=JSON.parse(event.data)
    if(message.id&&pending.has(message.id)){const p=pending.get(message.id);pending.delete(message.id);message.error?p.reject(Error(JSON.stringify(message.error))):p.resolve(message.result)}
    if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails.exception?.description||message.params.exceptionDetails.text)
    if(message.method==='Page.fileChooserOpened')choosers.push(message.params)
  })
  await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true})})
  await send('Runtime.enable');await send('Page.enable');await send('Page.setInterceptFileChooserDialog',{enabled:true})
  await send('Emulation.setDeviceMetricsOverride',{width:1100,height:1200,deviceScaleFactor:1,mobile:false})
  await send('Page.navigate',{url:`http://127.0.0.1:3400/${path.basename(fixture)}/index.html`})
  await waitFor("Boolean(window.attachmentTest)&&document.querySelectorAll('.file-uploader').length===2",'Form did not initialize')
  await click('删除',1)
  assert.deepEqual(await snapshot('attachmentTest.state.data.single'),['/uploads/second.pdf'],'删除单组附件必须保留其他文件')
  await click('删除')
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.需求说明书'),[],'删除必须同步到表单模型')
  assert.equal(await evaluate("Object.hasOwn(attachmentTest.state.data.attachment,'旧需求说明书')"),false,'删除必须清除历史分组别名，不能刷新后恢复')
  assert.equal(await evaluate("document.querySelectorAll('.file-uploader')[0].querySelectorAll('.file-item').length"),1)
  assert.equal(await evaluate('attachmentTest.preview.value.validate()'),false,'删除必填附件后必须阻止提交')
  await saveAndReload()
  await setFile('new.pdf')
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.需求说明书'),[{url:'/uploads/new.pdf',name:'new.pdf'}])
  await replace('replacement.pdf')
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.需求说明书'),[{url:'/uploads/replacement.pdf',name:'replacement.pdf'}],'达到数量上限时仍可替换，不能追加文件')
  assert.equal(await evaluate('attachmentTest.preview.value.validate()'),true)
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.设计说明书'),[{url:'/uploads/design.pdf',name:'design.pdf'}],'更新不能影响其他分组')
  await saveAndReload()

  // 类型、大小、取消和服务端失败都不能提前删除原附件。
  const original=await snapshot('attachmentTest.state.data')
  await replace('bad.txt');await replace('large.pdf')
  assert.equal(await evaluate('attachmentTest.state.uploads.length'),0,'校验失败不能发起上传')
  await evaluate('attachmentTest.state.fail=true')
  await replace('failed.pdf')
  assert.deepEqual(await snapshot('attachmentTest.state.data'),original,'上传失败必须保留原附件')
  await evaluate('attachmentTest.state.fail=false')
  await click('更新')
  await evaluate("document.querySelector('input[hidden][type=file]').dispatchEvent(new Event('cancel'))")
  assert.deepEqual(await snapshot('attachmentTest.state.data'),original,'取消选择必须保留原附件')

  // 上传中删除目标附件后，迟到响应不能恢复用户删除的附件。
  await evaluate('attachmentTest.state.delay=500')
  await replace('late.pdf')
  await click('删除')
  await waitFor("![...document.querySelectorAll('.file-uploader button')].some(b=>b.textContent.trim()==='更新'&&b.disabled)",'Replacement did not finish')
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.需求说明书'),[],'删除目标后上传响应不能复活附件')
  await saveAndReload()

  // 两个请求返回前切换分组；各自按发起时的分组回填，不能被最后一次选择覆盖。
  await evaluate('attachmentTest.state.delay=500')
  await setFile('requirements.pdf')
  await evaluate("document.querySelectorAll('.group-tag')[1].click()")
  await setFile('design-new.pdf')
  await waitFor('attachmentTest.state.data.attachment.需求说明书.length===1&&attachmentTest.state.data.attachment.设计说明书.length===2','Concurrent uploads lost their original group')
  assert.equal(await evaluate('attachmentTest.state.data.attachment.需求说明书[0].name'),'requirements.pdf')
  assert.equal(await evaluate('attachmentTest.state.data.attachment.设计说明书[1].name'),'design-new.pdf')
  await evaluate('attachmentTest.state.delay=0')
  await replace('replacement.pdf',2)
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.设计说明书'),[{url:'/uploads/design.pdf',name:'design.pdf'},{url:'/uploads/replacement.pdf',name:'replacement.pdf'}],'更新指定行应保留同组其他附件')
  await click('删除',0,2)
  assert.deepEqual(await snapshot('attachmentTest.state.data.attachment.设计说明书'),[{url:'/uploads/design.pdf',name:'design.pdf'}],'删除指定行不能清空整个分组')
  await click('删除',1)
  assert.deepEqual(await snapshot('attachmentTest.state.data.single'),[],'删除最后一份单组附件必须清空模型')
  await saveAndReload()
  await evaluate('attachmentTest.state.readonly=true;attachmentTest.settle()')
  assert.equal(await evaluate("[...document.querySelectorAll('button')].some(b=>['更新','删除'].includes(b.textContent.trim()))"),false,'只读状态不能显示修改入口')
  assert.deepEqual(errors,[],'上传不能重复触发成功回调')
  console.log('file attachment browser tests passed: delete/save/reload, replace at limit, required validation, failure/cancel, deleted target, concurrent groups, single file and readonly')
} catch(error) {
  if(ws?.readyState===WebSocket.OPEN){
    console.error(await evaluate('JSON.stringify({data:window.attachmentTest?.state.data,body:document.body.innerText})'))
    const screenshot=await send('Page.captureScreenshot',{format:'png'})
    writeFileSync('/tmp/flow-file-attachment-failure.png',Buffer.from(screenshot.data,'base64'))
  }
  throw error
} finally {
  ws?.close();browser?.kill();await server?.close();await sleep(200)
  for(const directory of [fixture,profile,uploadDir])rmSync(directory,{recursive:true,force:true,maxRetries:3,retryDelay:200})
}
