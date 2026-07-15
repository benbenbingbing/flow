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
            <el-descriptions-item label="组件类型">文本、多行、富文本、数字、日期、选择、开关、文件、级联、实体引用、子表单、分组标题。</el-descriptions-item>
            <el-descriptions-item label="组件参数">由组件 `configSchema` 自动生成，例如行数、长度、数值范围、小数位、步长、开关文本。</el-descriptions-item>
            <el-descriptions-item label="结构化校验">必填、最小/最大长度、最小/最大值、邮箱、手机号、URL。</el-descriptions-item>
            <el-descriptions-item label="运行模式权限">分别配置新增、编辑、审批、查看时是否显示、是否可编辑。</el-descriptions-item>
            <el-descriptions-item label="字段联动">显隐、禁用、必填、计算、值映射、选项联动。</el-descriptions-item>
            <el-descriptions-item label="布局">垂直、水平、栅格、字段跨度、分组标题、子表单嵌入或页签。</el-descriptions-item>
          </el-descriptions>
        </section>

        <section id="field-component" class="guide-section">
          <h3>3. 自定义表单项目组件</h3>
          <p>只替换某一类字段的输入控件时，注册字段组件即可，表单布局、校验、联动和模式权限继续由平台管理。</p>
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
          <h3>4. 整表单自定义组件</h3>
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
        </section>

        <section id="contract" class="guide-section">
          <h3>5. 整表单运行时契约</h3>
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
          <h3>6. 四种模式与只读规则</h3>
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
          <h3>7. 数据、校验和联动</h3>
          <ul class="check-list">
            <li>通过 `emit('update:modelValue', nextValue)` 更新业务字段对象。</li>
            <li>新增/编辑场景的整条记录、发起流程开关等信息在 `context.record` 中提供。</li>
            <li>读取 `linkageState.visibility / disabled / required / options / values`，不要另写一套联动引擎。</li>
            <li>通过 `defineExpose({ validate })` 暴露异步校验；返回 `false` 时平台阻止提交。</li>
            <li>服务端仍需校验字段类型、唯一性和业务规则，不能只依赖组件校验。</li>
          </ul>
        </section>

        <section id="security" class="guide-section">
          <h3>8. 安全边界</h3>
          <ul class="check-list">
            <li>组件注册名和配置参数会在后端校验格式、JSON 类型、嵌套深度和模式编码。</li>
            <li>不支持通过配置加载任意 Vue 文件、执行 JavaScript、Groovy 或自由 SQL。</li>
            <li>远程选项接口必须经过平台鉴权；不要在组件参数中保存令牌和密钥。</li>
            <li>查看和审批场景要按字段模式过滤，不得渲染明确配置为隐藏的敏感字段。</li>
            <li>组件未注册时自动回退默认动态表单。</li>
          </ul>
        </section>

        <section id="acceptance" class="guide-section">
          <h3>9. 验收清单</h3>
          <ul class="check-list">
            <li>新增、编辑、审批、查看四种模式分别验收显隐和编辑性。</li>
            <li>节点整表单只读与字段只读叠加后仍严格只读。</li>
            <li>结构化校验、唯一性校验、自定义 validate 都能阻止提交。</li>
            <li>表单切换、流程节点切换和多表单合并时字段 key 稳定。</li>
            <li>自定义组件异常或未注册时能安全回退。</li>
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
  { id: 'field-component', label: '字段组件' },
  { id: 'whole-form', label: '整表单组件' },
  { id: 'contract', label: '运行时契约' },
  { id: 'modes', label: '四种模式' },
  { id: 'data', label: '数据与校验' },
  { id: 'security', label: '安全边界' },
  { id: 'acceptance', label: '验收清单' }
]

const levels = [
  { level: '声明式表单项目', useCase: '常规字段、校验、联动、模式权限', entry: '表单设计器直接配置' },
  { level: '自定义字段组件', useCase: '评分、签名、坐标、业务选择器', entry: 'registerFormFieldComponent' },
  { level: '自定义整表单', useCase: '分步、矩阵、图形化复杂布局', entry: 'registerCustomFormComponent' }
]

const contractRows = [
  { name: 'modelValue', meaning: '统一的业务字段对象，使用 v-model 更新' },
  { name: 'form / fields', meaning: '表单配置和按当前模式过滤后的字段' },
  { name: 'readonly / mode', meaning: '整表单只读状态和 create/edit/approve/view 模式' },
  { name: 'linkageState', meaning: '显隐、禁用、必填、选项和值联动结果' },
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
