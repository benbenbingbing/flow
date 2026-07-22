<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>自定义列表组件</h2>
        <p>当表格配置不足以表达卡片、看板、树或地图时，接管列表展示，但继续复用平台数据、权限和操作能力。</p>
      </div>
      <el-tag type="warning">高定制入口</el-tag>
    </div>

    <div class="guide-layout">
      <main class="guide-content">
        <el-alert
          title="自定义列表只接管界面，不接管安全边界。列表数据范围、行操作能力、删除和审批校验仍由后端执行。"
          type="warning"
          :closable="false"
          show-icon
        />

        <section id="decision" class="guide-section">
          <h3>1. 什么时候需要整页自定义</h3>
          <el-table :data="decisionRows" border size="small">
            <el-table-column prop="requirement" label="需求" min-width="220" />
            <el-table-column prop="choice" label="建议" min-width="240" />
          </el-table>
        </section>

        <section id="register" class="guide-section">
          <h3>2. 注册组件与可视化参数</h3>
          <p>注册信息不仅包含 Vue 组件，还包含管理员可见名称、说明、参数 Schema 和支持能力。配置设计器据此生成选择项和参数表单。</p>
          <CodeCard title="custom-list-extension.ts" language="TypeScript">
            <pre v-pre><code>import { registerCustomListComponent } from '@/utils/customComponentRegistry'
import ProjectKanban from './ProjectKanban.vue'

registerCustomListComponent('ProjectKanban', ProjectKanban, {
  label: '项目看板',
  description: '按阶段分组展示项目卡片',
  configSchema: [
    {
      key: 'groupField',
      label: '分组字段',
      type: 'text',
      required: true,
      defaultValue: 'status'
    },
    {
      key: 'showOwner',
      label: '显示负责人',
      type: 'boolean',
      defaultValue: true
    }
  ],
  capabilities: {
    layout: 'kanban',
    supportsSelection: false
  }
})</code></pre>
          </CodeCard>
        </section>

        <section id="item-config" class="guide-section">
          <h3>3. 列、按钮和场景的单项扩展</h3>
          <p>只需要新增一列、改变一个单元格或增加一个按钮时，不要接管整页。每个列表项目都有稳定 `id`、稀疏 `orderKey` 和独立 `revision`。</p>
          <CodeCard title="PATCH 单个列表列" language="HTTP">
            <pre v-pre><code>PATCH /api/entity-list-config/lst_order/fields/col_risk
Content-Type: application/json

{
  "expectedRevision": 12,
  "field": {
    "fieldName": "风险等级",
    "dataSourceId": "ds_order_risk",
    "renderComponent": "RiskBadgeCell"
  }
}

200 OK
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "col_risk",
    "revision": 13
  }
}</code></pre>
          </CodeCard>
          <ul class="check-list">
            <li>列右侧保存当前列，按钮行保存当前按钮，场景勾选后即时保存当前场景；不同项目可以并行修改。</li>
            <li>同一项目 revision 不一致时返回 HTTP `409`，服务器当前项目位于响应 `data`；客户端以 `data.revision` 作为 serverRevision、以 `data` 作为 currentData，禁止静默覆盖。</li>
            <li>拖拽排序单独保存 `orderKey`，通常只更新被移动项目，不触碰其他项目的更新时间。</li>
            <li>旧整包保存接口仅用于导入兼容，后端按稳定 ID diff-upsert，不允许全删全插。</li>
          </ul>
        </section>

        <section id="contract" class="guide-section">
          <h3>4. 运行时契约 v3</h3>
          <el-alert
            title="所有标准和自定义列表都由 entityCode + listKey 唯一定位。菜单、弹窗、抽屉和表单选择器不得自行拼装另一套查询接口。"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-table :data="propsRows" border size="small">
            <el-table-column prop="name" label="Prop" width="190" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <p class="muted">旧版独立 props 保持兼容；新组件优先使用 `runtime` 聚合对象，后续平台扩展时不需要持续增加大量 props。</p>
          <CodeCard title="ProjectKanban.vue" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
const props = defineProps({
  entityCode: String,
  entityDefinition: Object,
  listConfig: Object,
  listFields: Array,
  queryFields: Array,
  queryForm: Object,
  dataList: Array,
  total: Number,
  pageNum: Number,
  pageSize: Number,
  loading: Boolean,
  tableLoading: Boolean,
  config: Object,
  runtime: Object
})

function edit(row) {
  if (!props.runtime.canAction(row, 'edit')) return
  props.runtime.edit(row)
}

function remove(row) {
  if (!props.runtime.canAction(row, 'delete')) return
  props.runtime.delete(row)
}
&lt;/script&gt;</code></pre>
          </CodeCard>
        </section>

        <section id="launcher" class="guide-section">
          <h3>5. 弹窗与抽屉复用</h3>
          <p>`EntityListLauncher` 根据同一个 listKey 打开弹窗或抽屉，并复用列表字段、数据范围、选择模式和返回映射。</p>
          <CodeCard title="CustomerPicker.vue" language="Vue">
            <pre v-pre><code>&lt;EntityListLauncher
  entity-code="customer"
  list-key="available_customer_picker"
  presentation="DIALOG"
  selection-mode="SINGLE"
  :context="{
    sourceEntityCode: 'sales_order',
    sourceRecordId: orderId,
    relationKey: 'order_customer'
  }"
  @confirm="handleCustomerSelected"
/&gt;</code></pre>
          </CodeCard>
          <p>列表设计器中的自定义按钮也可将“自定义模式”设为“打开列表”，直接配置目标实体、目标 listKey、弹窗/抽屉、选择方式、relationKey 和可选选择回调。</p>
          <p>表单的 CUSTOM 实体引用字段可在“选择列表”中指定 listKey，运行时以 `FORM_PICKER` 场景打开统一列表；选择型场景会自动隐藏新增、编辑、审批和删除等业务动作。</p>
          <p class="muted">后端只信任 `sourceEntityCode + sourceRecordId + relationKey`，并由 `EntityListContextResolver` 重新读取来源数据；前端参数不能扩大数据范围。</p>
        </section>

        <section id="runtime" class="guide-section">
          <h3>6. runtime 方法</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="reload()">重新加载当前列表。</el-descriptions-item>
            <el-descriptions-item label="search() / reset()">执行平台查询或重置查询条件。</el-descriptions-item>
            <el-descriptions-item label="create() / view(row) / edit(row)">调用平台标准弹窗和详情流程。</el-descriptions-item>
            <el-descriptions-item label="delete(row) / approve(row)">调用受后端强制权限保护的操作。</el-descriptions-item>
            <el-descriptions-item label="exportData(type)">执行选中或全部导出。</el-descriptions-item>
            <el-descriptions-item label="canAction(row,key)">读取后端返回的行能力。</el-descriptions-item>
            <el-descriptions-item label="getActionReason(row,key)">取得禁用或隐藏原因。</el-descriptions-item>
            <el-descriptions-item label="viewConfig">列表查询区、表格、分页和组件参数配置。</el-descriptions-item>
          </el-descriptions>
        </section>

        <section id="data-source" class="guide-section">
          <h3>7. LIST_QUERY 与 LIST_COLUMN 数据源</h3>
          <ul class="check-list">
            <li>整表查询绑定 `LIST_QUERY`，单列补充绑定 `LIST_COLUMN`；现有 `EntityListDataProvider` 和 `ListFieldDataProvider` 通过适配器继续使用。</li>
            <li>可选类型包括 `ENTITY_QUERY`、`DICTIONARY`、`STATIC_OPTIONS`、`REGISTERED_PROVIDER`、`INTEGRATION_CONNECTOR`、`RUNTIME_CONTEXT`、`STRUCTURED_COMPUTE`。</li>
            <li>数据源统一配置输入/输出映射、分页、超时、缓存和失败策略，并在发布前按 Schema 校验。</li>
            <li>所有查询都必须执行平台传入的 `DataScopePlan`；Provider 或 Connector 不得返回授权范围外记录。</li>
            <li>禁止任意 SQL、脚本和外网 URL。外部系统只能通过已注册 Connector 与凭据引用访问。</li>
          </ul>
        </section>

        <section id="release" class="guide-section">
          <h3>8. 草稿、发布、回滚与模板</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="/draft">设计器读取列、按钮、场景和列表顶层草稿；生产运行时不读取草稿。</el-descriptions-item>
            <el-descriptions-item label="/diff">按稳定 ID 展示新增、修改、移动和删除，并校验数据源、权限与组件依赖；响应中的 `changedItems[]` 返回 section、id、label、changeType、changedFields，`changedSections` 保留给旧客户端兼容。</el-descriptions-item>
            <el-descriptions-item label="/publish">创建不可变快照与内容哈希并原子激活；失败时旧 release 继续服务。</el-descriptions-item>
            <el-descriptions-item label="/releases">列出历史版本、发布人、发布时间、哈希和激活状态。</el-descriptions-item>
            <el-descriptions-item label="/activate">重新校验并激活历史 release，实现可审计回滚。</el-descriptions-item>
          </el-descriptions>
          <ul class="check-list">
            <li>列组和按钮组模板固定 `templateId + templateVersion + localOverrides`，不会自动跟随模板升级。</li>
            <li>显式升级使用旧模板、目标模板、本地覆盖三方合并；冲突逐项确认，结果只进入草稿。</li>
            <li>无需后续升级时使用“复制后独立”，复制内容不再保留模板关系。</li>
          </ul>
        </section>

        <section id="events" class="guide-section">
          <h3>9. 兼容事件</h3>
          <p>平台仍支持通过 emit 触发标准动作，适合较简单的自定义组件。</p>
          <CodeCard title="事件列表" language="Vue">
            <pre v-pre><code>const emit = defineEmits([
  'search', 'reset',
  'sizeChange', 'pageChange',
  'create', 'view', 'edit', 'delete', 'approve'
])

emit('edit', row)
emit('pageChange', 2)</code></pre>
          </CodeCard>
        </section>

        <section id="demo" class="guide-section">
          <h3>10. 可运行 Demo</h3>
          <ul class="check-list">
            <li>`src/demo/lists/DemoProjectCardList.vue`：卡片布局、查询、分页、空状态和标准行操作的完整实现。</li>
            <li>`src/demo/index.js`：以 `DemoProjectCardList` 注册组件，并声明列数、紧凑模式、说明和搜索提示参数。</li>
            <li>示例直接使用 `runtime.canAction / getActionReason` 和 `listConfig.toolbarCapabilities`，不在前端重写权限规则。</li>
            <li>执行 `npm run test:demo:real` 可创建真实实体、列表和流程；验证结果写入 `docs/dynamic-extension-demo/latest.json`。</li>
          </ul>
        </section>

        <section id="security" class="guide-section">
          <h3>11. 权限与安全</h3>
          <ul class="check-list">
            <li>不要根据“是否本人”“流程状态”在组件内重新实现权限规则，直接使用 `canAction`。</li>
            <li>按钮隐藏只是体验优化，真正操作仍会在后端重新加载最新数据并校验。</li>
            <li>不要接受配置中的任意脚本、任意组件路径或客户端提供的权限码。</li>
            <li>自定义远程请求必须复用平台请求封装，并明确加载、错误和取消状态。</li>
            <li>组件未注册时平台自动回退默认动态列表，避免配置错误导致页面不可用。</li>
            <li>自定义数据源必须实现 `EntityListDataProvider` 并执行平台传入的 `DataScopePlan`。</li>
            <li>打开列表按钮和表单选择器必须使用已发布的 entityCode + listKey，不得回退为无数据范围的自由 URL。</li>
            <li>整页组件只能读取当前激活 release 的 `runtime.viewConfig`；不得自行加载草稿接口或绕过发布。</li>
          </ul>
        </section>

        <section id="migration" class="guide-section">
          <h3>12. 迁移与发布回退</h3>
          <ul class="check-list">
            <li>迁移保留已有列、按钮和场景 ID；缺失 ID 时只生成一次，重复执行结果幂等。</li>
            <li>升级时为既有列表生成初始 release，并核对列数、查询结果、按钮能力和快照哈希。</li>
            <li>新运行时优先读取激活 release；不存在时仅临时回退旧配置并记录告警。</li>
            <li>整页自定义组件必须兼容旧快照参数；废弃字段通过版本迁移函数处理，不能直接破坏历史 release。</li>
          </ul>
        </section>

        <section id="acceptance" class="guide-section">
          <h3>13. 验收清单</h3>
          <ul class="check-list">
            <li>查询、重置、分页、加载状态和空状态完整。</li>
            <li>查看、编辑、删除、审批与默认列表能力一致。</li>
            <li>没有能力的操作不展示，禁用操作能看到原因。</li>
            <li>路由切换实体或列表配置后，不保留上一实体的本地状态。</li>
            <li>组件异常或未注册时能安全回退。</li>
            <li>表单选择器只显示选择能力，不出现新增、编辑、审批、删除等业务操作。</li>
            <li>修改一个列、按钮或场景后，其他项目的 ID、revision、更新时间和内容不变。</li>
            <li>草稿预览不影响线上，发布原子生效，历史 release 可 activate 回滚。</li>
            <li>模板不会自动级联，三方升级保留 localOverrides。</li>
          </ul>
        </section>
      </main>

      <aside class="guide-toc">
        <div class="toc-title">目录</div>
        <a v-for="item in toc" :key="item.id" :href="`#${item.id}`">{{ item.label }}</a>
      </aside>
    </div>
  </div>
</template>

<script setup>
import CodeCard from '@/components/dev-guide/CodeCard.vue'

const toc = [
  { id: 'decision', label: '选择边界' },
  { id: 'register', label: '注册与参数' },
  { id: 'item-config', label: '单项配置' },
  { id: 'contract', label: '运行时契约' },
  { id: 'launcher', label: '弹窗与抽屉' },
  { id: 'runtime', label: 'runtime 方法' },
  { id: 'data-source', label: '统一数据源' },
  { id: 'release', label: '发布与模板' },
  { id: 'events', label: '兼容事件' },
  { id: 'demo', label: '可运行 Demo' },
  { id: 'security', label: '权限与安全' },
  { id: 'migration', label: '迁移兼容' },
  { id: 'acceptance', label: '验收清单' }
]

const decisionRows = [
  { requirement: '改列宽、查询项、标签、日期格式', choice: '使用默认动态列表配置' },
  { requirement: '增加关联、聚合、派生列', choice: '列表字段数据提供者 + 单元格组件' },
  { requirement: '增加一个按钮或一组可升级列', choice: '单项按钮配置或版本化列组/按钮组模板' },
  { requirement: '卡片、看板、树形、地图布局', choice: '自定义列表组件' },
  { requirement: '整个页面包含额外业务流程', choice: '自定义列表组件，复用 runtime 标准操作' },
  { requirement: '只是局部视觉或数据变化', choice: '不要使用整页自定义，优先节点级/列级扩展' }
]

const propsRows = [
  { name: 'entityCode / entityDefinition', meaning: '当前实体编码与定义' },
  { name: 'entityName', meaning: '当前实体展示名称' },
  { name: 'listConfig / listConfigFields', meaning: '列表主配置与全部列表字段配置' },
  { name: 'listFields', meaning: '按 showInList 过滤后的实际展示字段' },
  { name: 'queryFields / queryForm', meaning: '查询项目和响应式查询值' },
  { name: 'dataList / total', meaning: '当前页数据与总数' },
  { name: 'pageNum / pageSize', meaning: '当前分页状态' },
  { name: 'config', meaning: '管理员按 configSchema 保存的组件参数' },
  { name: 'runtime', meaning: '平台操作、权限判断、刷新和视图配置聚合对象' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
