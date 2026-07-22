<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>自定义表单组件</h2>
        <p>从动态表单项目到整表单接管的统一组件契约，覆盖新增、编辑、审批和查看。</p>
      </div>
      <el-tag type="success">四种运行模式</el-tag>
    </div>

    <div class="guide-layout">
      <main class="guide-content">
        <el-alert
          title="优先使用内置字段组件、结构化校验、字段联动和模式权限。只有单个控件或整个布局确实无法配置时才写自定义组件。"
          type="info"
          :closable="false"
          show-icon
        />

        <section id="levels" class="guide-section">
          <h3>1. 三层扩展模型</h3>
          <el-table :data="levels" border size="small">
            <el-table-column prop="level" label="层级" width="170" />
            <el-table-column prop="useCase" label="适用场景" min-width="230" />
            <el-table-column prop="entry" label="入口" min-width="250" />
          </el-table>
        </section>

        <section id="configuration" class="guide-section">
          <h3>2. 不写代码能配置什么</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="递归节点">SECTION、GRID、TAB_SET、TAB、COLLAPSE、TEXT、FIELD、SUB_FORM、REPEATER、ACTION_SLOT。</el-descriptions-item>
            <el-descriptions-item label="组件参数">由组件 `configSchema` 自动生成，例如行数、长度、数值范围、小数位、步长、开关文本。</el-descriptions-item>
            <el-descriptions-item label="结构化校验">必填、最小/最大长度、最小/最大值、邮箱、手机号、URL。</el-descriptions-item>
            <el-descriptions-item label="运行模式权限">分别配置新增、编辑、审批、查看时是否显示、是否可编辑。</el-descriptions-item>
            <el-descriptions-item label="字段联动">显隐、禁用、必填、计算、值映射、选项联动。</el-descriptions-item>
            <el-descriptions-item label="布局与嵌套">区块、栅格、Tab、折叠面板、子表与明细表可递归组合，最大深度 8 层。</el-descriptions-item>
          </el-descriptions>
        </section>

        <section id="node-contract" class="guide-section">
          <h3>3. 稳定节点 ID 与单项保存</h3>
          <ul class="check-list">
            <li>每个布局和内容节点都使用稳定 `nodeId`；改名、拖拽、发布和模板升级不会重建 ID。</li>
            <li>右侧属性面板只 PATCH 当前节点，删除和同级排序使用独立接口；其他节点的 revision 与更新时间保持不变。</li>
            <li>请求必须携带 `expectedRevision`。同节点并发修改返回 HTTP `409`，不同节点允许并行保存。</li>
            <li>409 响应将服务器当前节点放在 `data`；设计器以 `data.revision` 作为 serverRevision、以 `data` 作为 currentData，保留本地编辑并提供逐字段合并，禁止自动覆盖。</li>
          </ul>
          <CodeCard title="新增与修改节点" language="HTTP">
            <pre v-pre><code>POST /api/entity-forms/frm_project/nodes
{
  "parentId": "section_basic",
  "nodeType": "FIELD",
  "bindingType": "ENTITY_FIELD",
  "bindingRef": "riskScore",
  "orderKey": 1000000
}

PATCH /api/entity-forms/frm_project/nodes/node_risk
{
  "expectedRevision": 3,
  "label": "项目风险评分",
  "props": { "max": 100 }
}</code></pre>
          </CodeCard>
        </section>

        <section id="node-types" class="guide-section">
          <h3>4. 节点类型、绑定与 8 层限制</h3>
          <el-table :data="nodeRows" border size="small">
            <el-table-column prop="type" label="nodeType" width="220" />
            <el-table-column prop="meaning" label="用途与约束" />
          </el-table>
          <ul class="check-list">
            <li>FIELD 可绑定实体字段、实体关系、计算字段或运行上下文字段；TEXT 和布局节点可以不绑定数据。</li>
            <li>SUB_FORM 必须引用子实体、关系和指定已发布表单版本；发布时检查跨表单循环引用。运行时每条子表行使用自己的 recordId 与数据对象执行子表 `FORM_INIT`、`AFTER_LOAD`、默认值和计算，不会污染父记录。</li>
            <li>树深度超过 8、TAB 不属于 TAB_SET、孤儿节点或父子类型不合法时禁止发布。</li>
            <li>历史 `componentProps` 中可识别的子表、引用、事件和选项迁移为显式属性，未知内容保存在 `legacyProps`。</li>
          </ul>
        </section>

        <section id="field-component" class="guide-section">
          <h3>5. 节点级自定义组件</h3>
          <p>只替换某一类字段或局部展示节点时，注册节点级组件即可，表单树、校验、数据源、联动、模式权限和发布快照继续由平台管理。</p>
          <CodeCard title="rating-field-extension.ts" language="TypeScript">
            <pre v-pre><code>import { registerFormFieldComponent } from '@/components/form-fields'
import RatingField from './RatingField.vue'

registerFormFieldComponent('rating', RatingField, {
  label: '评分',
  description: '1 到 N 星评分',
  supportedFieldTypes: ['INTEGER', 'DECIMAL'],
  configSchema: [
    { key: 'max', label: '最高分', type: 'number', defaultValue: 5 },
    { key: 'allowHalf', label: '允许半星', type: 'boolean', defaultValue: false }
  ]
})</code></pre>
          </CodeCard>
          <CodeCard title="node-extension.ts" language="TypeScript">
            <pre v-pre><code>registerFormNodeComponent('risk-matrix', RiskMatrixNode, {
  version: 3,
  nodeTypes: ['FIELD'],
  supportedBindings: ['ENTITY_FIELD', 'COMPUTED'],
  configSchema: [
    { key: 'levels', label: '风险等级', type: 'number', required: true }
  ],
  snapshotVersion: 1
})</code></pre>
          </CodeCard>
          <ul class="check-list">
            <li>前端注册只负责提供运行时代码；发布前还必须在设计器“扩展清单”登记同名 `NODE` manifest。</li>
            <li>设计器保存 `componentName + componentVersion + snapshotVersion`，不会自动跟随目标环境的最新版本。</li>
            <li>发布会校验启用状态、实现版本、节点类型、绑定类型和快照协议，任一不兼容都会阻止发布。</li>
          </ul>
          <CodeCard title="RatingField.vue" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
const props = defineProps({
  field: { type: Object, required: true },
  modelValue: [String, Number, Array, Object, Boolean],
  disabled: Boolean,
  options: Array
})

const emit = defineEmits([
  'update:modelValue', 'change', 'blur', 'focus'
])
&lt;/script&gt;</code></pre>
          </CodeCard>
        </section>

        <section id="whole-form" class="guide-section">
          <h3>6. 整表单自定义组件</h3>
          <p>复杂分步表单、矩阵录入、图形化编辑器等场景可以接管整个表单区域。`modelValue` 在所有场景中统一为业务字段对象，不再在新增场景传整条记录、审批场景传字段对象。</p>
          <CodeCard title="custom-form-extension.ts" language="TypeScript">
            <pre v-pre><code>import { registerCustomFormComponent } from '@/utils/customComponentRegistry'
import ProjectWizardForm from './ProjectWizardForm.vue'

registerCustomFormComponent('ProjectWizardForm', ProjectWizardForm, {
  label: '项目分步表单',
  description: '分阶段维护项目基础信息和计划',
  supportedModes: ['create', 'edit', 'approve', 'view'],
  configSchema: [
    { key: 'showSummary', label: '显示汇总', type: 'boolean', defaultValue: true },
    { key: 'defaultStep', label: '默认步骤', type: 'number', defaultValue: 0 }
  ]
})</code></pre>
          </CodeCard>
          <el-alert
            title="整表单扩展适用于分步、矩阵或图形编辑器；如果只是一个字段、区块、子表或动作槽不同，应使用节点级扩展。整表单也必须读取已发布快照和平台数据源运行时。"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>

        <section id="contract" class="guide-section">
          <h3>7. 整表单运行时契约</h3>
          <el-table :data="contractRows" border size="small">
            <el-table-column prop="name" label="Prop" width="190" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <CodeCard title="ProjectWizardForm.vue" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
import { ref } from 'vue'

const props = defineProps({
  form: Object,
  modelValue: Object,
  readonly: Boolean,
  fields: Array,
  linkageState: Object,
  mode: String,
  config: Object,
  context: Object,
  entityCode: String,
  entityDefinition: Object,
  entityFields: Array
})

const emit = defineEmits(['update:modelValue'])
const formRef = ref()

async function validate() {
  if (props.readonly || props.mode === 'view') return true
  return await formRef.value.validate()
}

defineExpose({ validate })
&lt;/script&gt;</code></pre>
          </CodeCard>
        </section>

        <section id="modes" class="guide-section">
          <h3>8. 四种模式与只读规则</h3>
          <el-table :data="modeRows" border size="small">
            <el-table-column prop="mode" label="mode" width="120" />
            <el-table-column prop="scene" label="场景" width="180" />
            <el-table-column prop="rule" label="组件责任" />
          </el-table>
          <el-alert
            title="readonly=true 或字段模式 editable=false 时必须禁用交互。自定义组件不能为了“方便选择”忽略只读配置。"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>

        <section id="data" class="guide-section">
          <h3>9. 数据、校验和联动</h3>
          <ul class="check-list">
            <li>通过 `emit('update:modelValue', nextValue)` 更新业务字段对象。</li>
            <li>新增/编辑场景的整条记录、发起流程开关等信息在 `context.record` 中提供。</li>
            <li>读取 `linkageState.visibility / disabled / required / options / values`，不要另写一套联动引擎。</li>
            <li>通过 `defineExpose({ validate })` 暴露异步校验；返回 `false` 时平台阻止提交。</li>
            <li>服务端仍需校验字段类型、唯一性和业务规则，不能只依赖组件校验。</li>
          </ul>
        </section>

        <section id="data-source" class="guide-section">
          <h3>10. 表单统一数据源</h3>
          <ul class="check-list">
            <li>类型包括 `ENTITY_QUERY`、`DICTIONARY`、`STATIC_OPTIONS`、`REGISTERED_PROVIDER`、`INTEGRATION_CONNECTOR`、`RUNTIME_CONTEXT`、`STRUCTURED_COMPUTE`。</li>
            <li>可绑定 `FORM_INIT`、`FIELD_OPTIONS`、`FIELD_DEFAULT`、`FIELD_COMPUTE`、`SUBFORM_ROWS`、`AFTER_LOAD`、`BEFORE_SUBMIT`。</li>
            <li>配置统一输入/输出映射、分页、超时、缓存和失败策略；组件只通过 `dataSourceRuntime.execute(bindingId, context)` 调用。</li>
            <li>实体查询始终执行 `DataScopePlan`；客户端上下文、隐藏字段和组件本地状态不能扩大数据权限。</li>
            <li>禁止任意 SQL、脚本和外网 URL。远程调用只能引用受控 Connector 与凭据引用，配置中不得保存令牌。</li>
            <li>生产运行时调用 `/api/ui-data-sources/{id}/execute`；`/preview` 仅供管理员在设计态调试，不能作为自定义组件运行接口。</li>
          </ul>
        </section>

        <section id="release-template" class="guide-section">
          <h3>11. 草稿发布、版本回滚与模板升级</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="/draft">节点设计器与草稿预览读取，包含节点 revision 和未发布状态。</el-descriptions-item>
            <el-descriptions-item label="/diff">比较草稿与当前激活 release，校验全树、数据源、关系、循环引用和权限；响应同时返回兼容的 `changedSections` 与 `changedItems[]`（section、id、label、changeType、changedFields），按稳定 ID 表示新增、修改、移动、删除。</el-descriptions-item>
            <el-descriptions-item label="/publish">创建不可变快照、内容哈希、发布人和发布时间并原子激活。</el-descriptions-item>
            <el-descriptions-item label="/releases">查看历史发布记录。</el-descriptions-item>
            <el-descriptions-item label="/activate">重新校验并激活历史版本；发布或激活失败时旧版本继续服务。</el-descriptions-item>
          </el-descriptions>
          <ul class="check-list">
            <li>字段组、区块和子表模板实例固定 `templateId + templateVersion + localOverrides`，不会自动跟随模板变化。</li>
            <li>显式升级使用旧模板、目标模板和本地覆盖三方合并；冲突逐项确认，升级结果只进入草稿。</li>
            <li>“复制后独立”不保留模板关系，适合无需后续升级的表单片段。</li>
          </ul>
        </section>

        <section id="demo" class="guide-section">
          <h3>12. 可运行 Demo</h3>
          <ul class="check-list">
            <li>`src/demo/forms/DemoProjectForm.vue`：统一业务字段对象、四种模式、字段级显隐/只读、联动状态和异步 `validate`。</li>
            <li>`src/demo/index.js`：以 `DemoProjectForm` 注册整表单组件，并声明副标题、强调色和风险提示参数。</li>
            <li>真实验证流程把该表单同时配置为新增默认表单和审批节点表单，审批后将风险评分从 58 回写为 35。</li>
            <li>执行 `npm run test:demo:real`；实体、流程、表单和数据 ID 记录在 `docs/dynamic-extension-demo/latest.json`。</li>
          </ul>
        </section>

        <section id="security" class="guide-section">
          <h3>13. 安全边界</h3>
          <ul class="check-list">
            <li>组件注册名和配置参数会在后端校验格式、JSON 类型、嵌套深度和模式编码。</li>
            <li>组件注册和 `configSchema` 参数不支持加载任意 Vue 文件，也不执行 JavaScript、Groovy 或自由 SQL。</li>
            <li>旧版字段事件 `componentProps.events` 仍兼容受信任开发脚本；不要向普通管理员或租户开放，新增逻辑优先使用结构化联动或已注册组件。</li>
            <li>远程选项接口必须经过平台鉴权；不要在组件参数中保存令牌和密钥。</li>
            <li>查看和审批场景要按字段模式过滤，不得渲染明确配置为隐藏的敏感字段。</li>
            <li>组件未注册时自动回退默认动态表单。</li>
            <li>自定义组件不得直接调用草稿接口；生产渲染只使用当前激活 release。</li>
          </ul>
        </section>

        <section id="migration" class="guide-section">
          <h3>14. 迁移、版本兼容与回退</h3>
          <ul class="check-list">
            <li>旧表单字段幂等转换为一级 FIELD 节点，重复迁移不会再次生成 nodeId。</li>
            <li>未知历史属性写入 `legacyProps` 和迁移报告；报告同时输出节点数、快照哈希和初始 release。启动迁移按单表单/单列表独立事务执行，失败项只输出结构化失败报告，不回滚已成功迁移和发布的其他配置。</li>
            <li>新运行时优先读取激活 release；不存在时仅临时回退旧配置并记录告警。</li>
            <li>节点组件通过 `snapshotVersion` 与迁移函数兼容旧发布快照；废弃注册名必须提供替代和过渡期。</li>
            <li>实体迁移包携带递归节点和引用的扩展 manifest；导入时先登记 manifest，再按 `nodeKey / parentNodeKey` 重建节点树。</li>
            <li>manifest 不包含可执行代码，目标环境仍须先部署对应前端组件，再激活发布快照。</li>
          </ul>
        </section>

        <section id="acceptance" class="guide-section">
          <h3>15. 验收清单</h3>
          <ul class="check-list">
            <li>新增、编辑、审批、查看四种模式分别验收显隐和编辑性。</li>
            <li>节点整表单只读与字段只读叠加后仍严格只读。</li>
            <li>结构化校验、唯一性校验、自定义 validate 都能阻止提交。</li>
            <li>表单切换、流程节点切换和多表单合并时字段 key 稳定。</li>
            <li>自定义组件异常或未注册时能安全回退。</li>
            <li>修改一个节点后其他节点的 ID、revision、更新时间和内容完全不变。</li>
            <li>循环引用和超过 8 层嵌套被发布校验拒绝。</li>
            <li>草稿不影响线上，发布原子生效，历史 release 可 activate 回滚。</li>
            <li>模板三方升级保留 localOverrides，迁移可重复执行且快照哈希可核对。</li>
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
  { id: 'levels', label: '扩展模型' },
  { id: 'configuration', label: '配置能力' },
  { id: 'node-contract', label: '单项与并发' },
  { id: 'node-types', label: '递归节点' },
  { id: 'field-component', label: '字段组件' },
  { id: 'whole-form', label: '整表单组件' },
  { id: 'contract', label: '运行时契约' },
  { id: 'modes', label: '四种模式' },
  { id: 'data', label: '数据与校验' },
  { id: 'data-source', label: '统一数据源' },
  { id: 'release-template', label: '发布与模板' },
  { id: 'demo', label: '可运行 Demo' },
  { id: 'security', label: '安全边界' },
  { id: 'migration', label: '迁移兼容' },
  { id: 'acceptance', label: '验收清单' }
]

const levels = [
  { level: '声明式递归节点', useCase: '区块、栅格、Tab、字段、子表、明细和动作槽', entry: '表单设计器按节点配置' },
  { level: '节点级组件', useCase: '评分、签名、坐标、业务选择器或局部复杂展示', entry: 'registerFormNodeComponent / registerFormFieldComponent' },
  { level: '自定义整表单', useCase: '分步、矩阵、图形化且节点树无法表达的整体交互', entry: 'registerCustomFormComponent，仍复用发布与权限运行时' }
]

const nodeRows = [
  { type: 'SECTION / GRID', meaning: '业务区块和栅格容器，可递归包含布局与内容节点' },
  { type: 'TAB_SET / TAB', meaning: '页签集合与页签内容，父子关系固定' },
  { type: 'COLLAPSE / TEXT', meaning: '折叠容器与无数据说明节点，TEXT 不执行脚本' },
  { type: 'FIELD', meaning: '绑定实体字段、关系、计算字段或上下文字段' },
  { type: 'SUB_FORM', meaning: '引用子实体、关系和指定已发布表单版本' },
  { type: 'REPEATER', meaning: '一对多明细容器，通过 SUBFORM_ROWS 加载' },
  { type: 'ACTION_SLOT', meaning: '受控动作插槽，权限与后端操作仍由平台校验' }
]

const contractRows = [
  { name: 'modelValue', meaning: '统一的业务字段对象，使用 v-model 更新' },
  { name: 'form / nodes', meaning: '当前激活发布快照和按当前模式过滤后的递归节点树' },
  { name: 'readonly / mode', meaning: '整表单只读状态和 create/edit/approve/view 模式' },
  { name: 'linkageState', meaning: '显隐、禁用、必填、选项和值联动结果' },
  { name: 'dataSourceRuntime', meaning: '受控执行 FORM_INIT、FIELD_OPTIONS、SUBFORM_ROWS 等绑定' },
  { name: 'config', meaning: '管理员按 configSchema 保存的组件参数' },
  { name: 'context', meaning: '实体、整条记录、当前模式等运行时上下文' }
]

const modeRows = [
  { mode: 'create', scene: '新增数据', rule: '可配置默认值和新增时可见/可编辑字段' },
  { mode: 'edit', scene: '编辑数据', rule: '遵循编辑模式字段权限和最新数据回显' },
  { mode: 'approve', scene: '流程审批', rule: '仅允许节点表单与字段配置允许编辑的项目' },
  { mode: 'view', scene: '详情查看', rule: '全部交互只读，隐藏字段不渲染' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
