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

        <section id="contract" class="guide-section">
          <h3>3. 运行时契约 v2</h3>
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

        <section id="runtime" class="guide-section">
          <h3>4. runtime 方法</h3>
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

        <section id="events" class="guide-section">
          <h3>5. 兼容事件</h3>
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

        <section id="security" class="guide-section">
          <h3>6. 权限与安全</h3>
          <ul class="check-list">
            <li>不要根据“是否本人”“流程状态”在组件内重新实现权限规则，直接使用 `canAction`。</li>
            <li>按钮隐藏只是体验优化，真正操作仍会在后端重新加载最新数据并校验。</li>
            <li>不要接受配置中的任意脚本、任意组件路径或客户端提供的权限码。</li>
            <li>自定义远程请求必须复用平台请求封装，并明确加载、错误和取消状态。</li>
            <li>组件未注册时平台自动回退默认动态列表，避免配置错误导致页面不可用。</li>
          </ul>
        </section>

        <section id="acceptance" class="guide-section">
          <h3>7. 验收清单</h3>
          <ul class="check-list">
            <li>查询、重置、分页、加载状态和空状态完整。</li>
            <li>查看、编辑、删除、审批与默认列表能力一致。</li>
            <li>没有能力的操作不展示，禁用操作能看到原因。</li>
            <li>路由切换实体或列表配置后，不保留上一实体的本地状态。</li>
            <li>组件异常或未注册时能安全回退。</li>
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
  { id: 'contract', label: '运行时契约' },
  { id: 'runtime', label: 'runtime 方法' },
  { id: 'events', label: '兼容事件' },
  { id: 'security', label: '权限与安全' },
  { id: 'acceptance', label: '验收清单' }
]

const decisionRows = [
  { requirement: '改列宽、查询项、标签、日期格式', choice: '使用默认动态列表配置' },
  { requirement: '增加关联、聚合、派生列', choice: '列表字段数据提供者 + 单元格组件' },
  { requirement: '卡片、看板、树形、地图布局', choice: '自定义列表组件' },
  { requirement: '整个页面包含额外业务流程', choice: '自定义列表组件，复用 runtime 标准操作' }
]

const propsRows = [
  { name: 'entityCode / entityDefinition', meaning: '当前实体编码与定义' },
  { name: 'listConfig / listFields', meaning: '列表配置与实际展示字段' },
  { name: 'queryFields / queryForm', meaning: '查询项目和响应式查询值' },
  { name: 'dataList / total', meaning: '当前页数据与总数' },
  { name: 'pageNum / pageSize', meaning: '当前分页状态' },
  { name: 'config', meaning: '管理员按 configSchema 保存的组件参数' },
  { name: 'runtime', meaning: '平台操作、权限判断、刷新和视图配置聚合对象' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
