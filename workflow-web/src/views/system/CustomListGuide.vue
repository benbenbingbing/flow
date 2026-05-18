<template>
  <div class="dev-guide-page">
    <div class="page-header">
      <h2>自定义列表组件开发指南</h2>
    </div>

    <div class="detail-sections">
      <section class="doc-section">
        <div class="section-title">自定义列表组件</div>
        <div class="section-content">
          <p class="section-intro">
            当默认的列表布局（查询条件 + 数据表格）无法满足业务需求时，可以编写自定义 Vue 组件完全接管列表页面的渲染。
          </p>

          <div class="subsection">
            <div class="subsection-title">1. 创建自定义列表组件</div>
            <el-card shadow="never" class="code-block-card">
              <template #header>
                <div class="code-header">
                  <el-tag size="small" type="success">Vue</el-tag>
                  <span class="code-title">CustomProjectList.vue</span>
                </div>
              </template>
              <pre class="code-block" v-pre><code>&lt;template&gt;
  &lt;div class="custom-project-list"&gt;
    &lt;!-- 自定义查询面板 --&gt;
    &lt;div class="custom-search"&gt;
      &lt;el-input v-model="localQuery.keyword" placeholder="搜索项目名称" /&gt;
      &lt;el-button type="primary" @click="onSearch"&gt;查询&lt;/el-button&gt;
      &lt;el-button @click="onReset"&gt;重置&lt;/el-button&gt;
      &lt;el-button type="success" @click="onCreate"&gt;+ 新增&lt;/el-button&gt;
    &lt;/div&gt;

    &lt;!-- 自定义卡片式列表 --&gt;
    &lt;div class="card-list"&gt;
      &lt;el-card v-for="row in dataList" :key="row.id" class="project-card"&gt;
        &lt;h4&gt;{{ row.name }}&lt;/h4&gt;
        &lt;p&gt;编号：{{ row.dataNo }}&lt;/p&gt;
        &lt;p&gt;状态：&lt;el-tag :type="getStatusType(row.status)"&gt;{{ getStatusText(row.status) }}&lt;/el-tag&gt;&lt;/p&gt;
        &lt;div class="card-actions"&gt;
          &lt;el-button link type="primary" @click="onView(row)"&gt;查看&lt;/el-button&gt;
          &lt;el-button link type="primary" @click="onEdit(row)"&gt;编辑&lt;/el-button&gt;
          &lt;el-button v-if="canApprove(row)" link type="warning" @click="onApprove(row)"&gt;审批&lt;/el-button&gt;
          &lt;el-button link type="danger" @click="onDelete(row)"&gt;删除&lt;/el-button&gt;
        &lt;/div&gt;
      &lt;/el-card&gt;
    &lt;/div&gt;

    &lt;!-- 分页 --&gt;
    &lt;el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @size-change="onSizeChange"
      @current-change="onPageChange"
    /&gt;
  &lt;/div&gt;
&lt;/template&gt;

&lt;script setup&gt;
import { ref, watch } from 'vue'

const props = defineProps({
  entityCode: String,
  entityDefinition: Object,
  entityName: String,
  listConfig: Object,
  listConfigFields: Array,
  listFields: Array,
  queryFields: Array,
  queryForm: Object,
  dataList: Array,
  loading: Boolean,
  tableLoading: Boolean,
  total: Number,
  pageNum: Number,
  pageSize: Number,
  canApprove: Function,
  getStatusType: Function,
  getStatusText: Function,
  formatDate: Function
})

const emit = defineEmits([
  'search', 'reset', 'sizeChange', 'pageChange',
  'create', 'view', 'edit', 'delete', 'approve'
])

const localQuery = ref({ keyword: '' })

function onSearch() { emit('search') }
function onReset() { emit('reset') }
function onSizeChange(size) { emit('sizeChange', size) }
function onPageChange(page) { emit('pageChange', page) }
function onCreate() { emit('create') }
function onView(row) { emit('view', row) }
function onEdit(row) { emit('edit', row) }
function onDelete(row) { emit('delete', row) }
function onApprove(row) { emit('approve', row) }
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
import { registerCustomListComponent } from '@/utils/customComponentRegistry.js'
import CustomProjectList from '@/components/custom/CustomProjectList.vue'

registerCustomListComponent('CustomProjectList', CustomProjectList)</code></pre>
            </el-card>
          </div>

          <div class="subsection">
            <div class="subsection-title">3. Props 接口说明</div>
            <el-card shadow="never" class="code-block-card">
              <template #header>
                <div class="code-header">
                  <el-tag size="small" type="success">JavaScript</el-tag>
                  <span class="code-title">自定义列表组件 Props</span>
                </div>
              </template>
              <pre class="code-block" v-pre><code>// 自定义列表组件接收以下 props：

props: {
  entityCode: String,           // 实体编码
  entityDefinition: Object,     // 实体定义对象（含 enableProcess 等）
  entityName: String,           // 实体显示名称
  listConfig: Object,           // 列表配置对象
  listConfigFields: Array,      // 列表字段配置（原始配置）
  listFields: Array,            // 实际显示的列表字段
  queryFields: Array,           // 查询字段数组
  queryForm: Object,            // 当前查询条件对象（响应式）
  dataList: Array,              // 当前页数据列表
  loading: Boolean,             // 页面整体加载状态
  tableLoading: Boolean,        // 表格/数据加载状态
  total: Number,                // 总记录数
  pageNum: Number,              // 当前页码
  pageSize: Number,             // 每页大小
  canApprove: Function,         // (row) => boolean 判断是否可审批
  getStatusType: Function,      // (status) => string 获取状态样式
  getStatusText: Function,      // (status) => string 获取状态文本
  formatDate: Function          // (dateStr) => string 格式化日期
}

// 通过 emit 触发以下事件回调：
emits: [
  'search',       // 触发查询
  'reset',        // 触发重置
  'sizeChange',   // 分页大小变化 (size)
  'pageChange',   // 页码变化 (page)
  'create',       // 新增数据
  'view',         // 查看数据 (row)
  'edit',         // 编辑数据 (row)
  'delete',       // 删除数据 (row)
  'approve'       // 审批数据 (row)
]</code></pre>
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
              <pre class="code-block" v-pre><code>1. 编写自定义列表 Vue 组件并注册到 customComponentRegistry

2. 进入「实体管理」→ 选择实体 →「列表配置」→「设计」

3. 在「字段配置」卡片上方的「自定义列表组件」输入框中填写组件注册名

4. 保存配置后，进入实体数据列表页面即可看到自定义渲染效果

5. 如果组件未注册或名称为空，自动回退到默认列表渲染</code></pre>
            </el-card>
          </div>

          <div class="subsection">
            <div class="subsection-title">5. 注意事项</div>
            <div class="section-tips">
              <ul>
                <li>自定义列表组件<strong>完全接管</strong>列表区域的渲染，包括查询条件、数据展示、分页等</li>
                <li>建议复用传入的 <code>queryForm</code>、<code>onSearch</code> 等回调，以保持与后端的数据交互逻辑一致</li>
                <li>分页为前端内存分页，所有数据已一次性加载到 <code>dataList</code> 中</li>
                <li>如果自定义组件未注册，系统会自动回退到默认的表格渲染，不影响正常使用</li>
                <li>自定义列表组件文件建议放在 <code>src/components/custom/</code> 目录下</li>
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
