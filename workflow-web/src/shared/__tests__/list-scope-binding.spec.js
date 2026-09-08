import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '../..')
const entityDesign = readFileSync(join(root, 'views/EntityDesign.vue'), 'utf8')
const listDesign = readFileSync(join(root, 'views/EntityListConfigDesign.vue'), 'utf8')
const formDesign = readFileSync(join(root, 'views/EntityFormDesignByEntity.vue'), 'utf8')
const formSettings = readFileSync(
  join(root, 'components/form-designer/FormDesignerSettingsDrawer.vue'),
  'utf8'
)
const manual = readFileSync(join(root, 'data/user-manual/entity.js'), 'utf8')

assert.match(
  entityDesign,
  /当前用户是相关人（参与过该记录）/,
  '实体规则目录必须提供 TEAM 范围'
)
assert.match(
  entityDesign,
  /value="HAS_TODO"/,
  '实体规则目录必须提供存在待办范围'
)
assert.doesNotMatch(
  entityDesign,
  /label="当前用户是当前办理人"/,
  '数据范围不再提供不准的当前办理人选项'
)
assert.match(
  entityDesign,
  /已停用，请改用存在待办/,
  '旧的当前办理人规则编辑时需提示改用存在待办'
)
assert.doesNotMatch(
  entityDesign,
  /permissionForm\.listKey/,
  '规则编辑不应再选择适用列表'
)
assert.doesNotMatch(
  entityDesign,
  /留空表示实体默认范围/,
  '规则编辑不应再提供实体默认范围'
)
assert.match(
  entityDesign,
  /已绑定列表/,
  '规则表应展示已绑定列表'
)
assert.match(
  entityDesign,
  /未绑定任何允许规则时，将执行该列表配置的安全默认策略/,
  '实体权限页应说明未绑定时执行安全默认策略'
)
assert.doesNotMatch(
  entityDesign,
  /savePermissionDraft|publishPermissions|保存权限草稿|校验并发布权限/,
  '实体权限弹窗不应再提供草稿保存和单独发布'
)
assert.match(
  entityDesign,
  /min\(1440px, 94vw\)/,
  '实体权限弹窗应使用更宽的视口宽度'
)

assert.match(
  listDesign,
  /绑定数据规则/,
  '列表设置应提供规则绑定'
)
assert.match(
  listDesign,
  /拒绝全部数据（推荐）/,
  '列表空绑定必须默认拒绝全部数据'
)
assert.match(listDesign, /仅本人创建或提交/, '列表应支持 PERSONAL 安全默认策略')
assert.match(listDesign, /确认继续全量可见/, 'EXPLICIT_ALL 必须提供显式确认入口')
assert.match(listDesign, /存量观察期/, '存量宽松配置必须展示观察期风险')
assert.match(
  listDesign,
  /replaceListBindings/,
  '保存列表设置应覆盖该列表绑定'
)
assert.match(
  listDesign,
  /数据规则绑定已立即生效/,
  '绑定保存后必须提示权限已生效，避免只改草稿'
)
assert.match(
  listDesign,
  /scopeBindingDirty/,
  '勾选或取消数据规则必须进入未保存状态'
)
assert.match(
  listDesign,
  /saveScopeBindings/,
  '只改数据规则绑定时也必须能单独保存'
)
assert.match(
  listDesign,
  /数据规则绑定已生效。列表界面配置没有需要发布的修改/,
  '只改绑定后点发布不能再报列表草稿已一致'
)
assert.match(listDesign, /openLinkedListEventBindings/, '列表设计器必须消费事件配置深链')
assert.match(listDesign, /route\.query\.targetType/, '列表事件深链必须保留按钮目标')
assert.match(formDesign, /route\.query\.targetType/, '表单事件深链必须保留字段或按钮目标')
assert.match(
  formSettings,
  /@changed="onEventBindingsChanged"/,
  '表单事件保存后必须立即刷新草稿与发布差异'
)
assert.match(
  formDesign,
  /onEventBindingsChanged: loadDiff/,
  '表单设置必须把事件变更接回统一差异刷新链'
)

assert.match(manual, /列表未绑定规则时默认拒绝/)
assert.match(manual, /DENY_ALL、PERSONAL 或经管理员确认的 EXPLICIT_ALL/)
assert.match(manual, /尚未办理的下一审批人不会进入相关人/)
assert.match(manual, /HAS_TODO/)
assert.match(manual, /TEAM 只看已参与记录/)
assert.match(manual, /TEAM/)
assert.match(entityDesign, /value="SQL"/, '数据范围和适用对象必须提供受控 SQL')
assert.match(entityDesign, /主表别名统一写 <code>biz<\/code>/, '必须说明主表别名 biz')
assert.match(entityDesign, /#\{userId\}/, '必须提供当前用户占位符')
assert.match(entityDesign, /scopeType === 'SQL'/, '适用对象 SQL 不能要求选择用户目标')
assert.match(entityDesign, /sql: form.filterType === 'SQL' \? String\(form.filterSql/, '保存规则必须带上数据范围 SQL')
assert.match(entityDesign, /sql: c.scopeType === 'SQL' \? String\(c.sql/, '保存规则必须带上适用对象 SQL')
assert.doesNotMatch(entityDesign, /value="CUSTOM_SQL"/, '不得再暴露未校验的 CUSTOM_SQL')
