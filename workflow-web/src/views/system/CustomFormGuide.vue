<template>
  <div class="dev-guide-page">
    <div class="page-header">
      <h2>自定义表单组件开发指南</h2>
    </div>

    <div class="detail-sections">
      <section class="doc-section">
        <div class="section-title">自定义表单组件</div>
        <div class="section-content">
          <p class="section-intro">
            当默认的表单字段渲染无法满足复杂业务需求时，可以编写自定义 Vue 组件完全接管表单的渲染，适用于数据录入和审批场景。
          </p>

          <div class="subsection">
            <div class="subsection-title">1. 创建自定义表单组件</div>
            <el-card shadow="never" class="code-block-card">
              <template #header>
                <div class="code-header">
                  <el-tag size="small" type="success">Vue</el-tag>
                  <span class="code-title">CustomProjectForm.vue</span>
                </div>
              </template>
              <pre class="code-block" v-pre><code>&lt;template&gt;
  &lt;div class="custom-project-form"&gt;
    &lt;el-form :model="localData" label-width="100px"&gt;
      &lt;el-form-item label="项目名称" required&gt;
        &lt;el-input v-model="localData.name" /&gt;
      &lt;/el-form-item&gt;

      &lt;el-form-item label="项目详情"&gt;
        &lt;el-input v-model="localData.data.detail" type="textarea" rows="4" /&gt;
      &lt;/el-form-item&gt;

      &lt;el-form-item label="优先级"&gt;
        &lt;el-rate v-model="localData.data.priority" :max="5" /&gt;
      &lt;/el-form-item&gt;

      &lt;!-- 自定义字段映射 --&gt;
      &lt;el-form-item label="负责人"&gt;
        &lt;EntitySelector
          v-model="localData.data.managerId"
          entity-type="USER"
        /&gt;
      &lt;/el-form-item&gt;
    &lt;/el-form&gt;
  &lt;/div&gt;
&lt;/template&gt;

&lt;script setup&gt;
import { ref, watch } from 'vue'

const props = defineProps({
  // 审批/表单预览场景
  form: Object,
  modelValue: Object,
  readonly: Boolean,
  fields: Array,
  linkageState: Object,
  // 数据录入场景
  entityCode: String,
  entityDefinition: Object,
  entityFields: Array,
  mode: String  // 'create' | 'edit'
})

const emit = defineEmits(['update:modelValue'])

const localData = ref({ ...props.modelValue })

watch(localData, (val) =&gt; {
  emit('update:modelValue', { ...val })
}, { deep: true })
&lt;/script&gt;</code></pre>
            </el-card>
          </div>

          <div class="subsection">
            <div class="subsection-title">2. 注册组件</div>
            <el-card shadow="never" class="code-block-card">
              <template #header>
                <div class="code-header">
                  <el-tag size="small" type="success">JavaScript</el-tag>
                  <span class="code-title">main.js</span>
                </div>
              </template>
              <pre class="code-block" v-pre><code>// 在 src/main.js 或任意初始化文件中
import { registerCustomFormComponent } from '@/utils/customComponentRegistry.js'
import CustomProjectForm from '@/components/custom/CustomProjectForm.vue'

registerCustomFormComponent('CustomProjectForm', CustomProjectForm)</code></pre>
            </el-card>
          </div>

          <div class="subsection">
            <div class="subsection-title">3. Props 接口说明</div>
            <el-card shadow="never" class="code-block-card">
              <template #header>
                <div class="code-header">
                  <el-tag size="small" type="success">JavaScript</el-tag>
                  <span class="code-title">自定义表单组件 Props</span>
                </div>
              </template>
              <pre class="code-block" v-pre><code>// 自定义表单组件在审批/预览场景接收以下 props：

props: {
  form: Object,           // 表单配置对象（含 formName、layoutType、fields 等）
  modelValue: Object,     // 表单数据对象（v-model 绑定）
  readonly: Boolean,      // 是否只读
  fields: Array,          // 处理后的字段数组（已排序）
  linkageState: Object    // 联动状态 { visibility, disabled, required, options, values }
}

// 在数据录入场景（新增/编辑弹窗）额外接收：
props: {
  entityCode: String,        // 实体编码
  entityDefinition: Object,  // 实体定义对象
  entityFields: Array,       // 实体字段数组
  mode: String               // 'create' 或 'edit'
}

// 必须 emit 的事件：
emits: ['update:modelValue']  // 更新表单数据</code></pre>
            </el-card>
          </div>

          <div class="subsection">
            <div class="subsection-title">4. 配置步骤</div>
            <el-card shadow="never" class="code-block-card">
              <template #header>
                <div class="code-header">
                  <el-tag size="small">步骤</el-tag>
                  <span class="code-title">操作流程</span>
                </div>
              </template>
              <pre class="code-block" v-pre><code>1. 编写自定义表单 Vue 组件并注册到 customComponentRegistry

2. 进入「实体管理」→ 选择实体 →「表单」→「设计」

3. 在表单基本信息区域的「自定义组件」输入框中填写组件注册名

4. 保存配置后，该表单在以下场景会使用自定义渲染：
   - 流程审批弹窗中的表单展示
   - 数据录入/编辑弹窗（当该表单被设为默认表单时）

5. 如果组件未注册或名称为空，自动回退到默认表单渲染</code></pre>
            </el-card>
          </div>

          <div class="subsection">
            <div class="subsection-title">5. 注意事项</div>
            <div class="section-tips">
              <ul>
                <li>自定义表单组件<strong>完全接管</strong>表单区域的渲染，需自行处理所有字段的输入绑定</li>
                <li>必须通过 <code>emit('update:modelValue', data)</code> 同步数据变化，否则保存时数据会丢失</li>
                <li>审批场景下 <code>readonly=true</code> 时，所有字段应处于不可编辑状态</li>
                <li>数据录入场景下，<code>modelValue</code> 包含完整表单数据：{ name, data: {...}, startProcess }</li>
                <li>如需表单校验，可通过 <code>defineExpose({ validate: () => Promise }) </code> 暴露校验方法</li>
                <li>如果自定义组件未注册，系统会自动回退到默认的字段渲染，不影响正常使用</li>
                <li>自定义表单组件文件建议放在 <code>src/components/custom/</code> 目录下</li>
              </ul>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight, ArrowDown, ArrowLeft } from '@element-plus/icons-vue'
</script>

<style scoped lang="scss">
.dev-guide-page {
  padding: 20px;

  .page-header {
    margin-bottom: 20px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }
  }
}

.doc-section {
  margin-bottom: 32px;

  .section-title {
    font-size: 17px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 16px;
    padding-left: 12px;
    border-left: 4px solid #409eff;
  }
}

.subsection {
  margin-bottom: 24px;

  .subsection-title {
    font-size: 15px;
    font-weight: 500;
    color: #303133;
    margin-bottom: 12px;
  }
}

.section-content {
  .section-intro {
    color: #606266;
    margin-bottom: 16px;
    line-height: 1.6;
  }

  .code-block-card {
    margin-bottom: 16px;

    :deep(.el-card__header) {
      padding: 10px 16px;
      background-color: #f5f7fa;
    }

    .code-header {
      display: flex;
      align-items: center;
      gap: 10px;

      .code-title {
        font-weight: 500;
        color: #303133;
      }
    }
  }

  .code-block {
    margin: 0;
    padding: 12px;
    background-color: #f8f9fa;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    line-height: 1.6;
    color: #333;
    white-space: pre-wrap;
    word-break: break-word;
    overflow-x: auto;
  }
}

.section-tips {
  ul {
    margin: 8px 0;
    padding-left: 20px;

    li {
      color: #606266;
      line-height: 1.8;
      font-size: 13px;

      code {
        background-color: #f4f4f5;
        padding: 1px 5px;
        border-radius: 3px;
        font-family: 'Consolas', 'Monaco', monospace;
        font-size: 12px;
        color: #d32f2f;
      }
    }
  }
}
</style>
