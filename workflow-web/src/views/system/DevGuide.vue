<template>
  <div class="dev-guide-page">
    <div class="page-header">
      <h2>列表字段扩展开发指南</h2>
    </div>

    <div class="content-layout">
      <!-- 左侧主内容 -->
      <div class="main-content">
        <el-alert
          title="通过 ListFieldDataProvider 接口扩展列表数据源，支持关联查询、聚合统计和自定义业务逻辑计算；通过 ListCellRenderer 机制注册自定义单元格渲染组件。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />

        <div class="detail-sections">
          <!-- 一、后端 -->
          <section id="section0" class="doc-section">
            <div class="section-title">一、后端：实现 ListFieldDataProvider 接口</div>
            <div class="section-content">
              <p class="section-intro">
                创建一个新的 Spring Bean，实现 ListFieldDataProvider 接口，在 enrich 方法中补充自定义列数据。
              </p>

              <div id="section0-0" class="subsection">
                <div class="subsection-title">1. 创建自定义数据提供者</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="danger">Java</el-tag>
                      <span class="code-title">UserNameFieldProvider.java</span>
                    </div>
                  </template>
                  <pre class="code-block"><code>package com.workflow.service.listfield;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 示例：通过用户ID关联查询用户名称
 */
@Component
public class UserNameFieldProvider implements ListFieldDataProvider {

    @Override
    public String getDataSourceType() {
        // 返回支持的数据源类型
        return "CUSTOM_PROVIDER";
    }

    @Override
    public void enrich(List&lt;EntityDataDTO&gt; records, List&lt;EntityListField&gt; fields,
                       Map&lt;String, Object&gt; context) {
        // 获取所有记录的用户ID（假设字段编码为 userId）
        List&lt;String&gt; userIds = records.stream()
            .map(r -&gt; {
                Object val = r.getData() != null ? r.getData().get("userId") : null;
                return val != null ? val.toString() : null;
            })
            .filter(id -&gt; id != null &amp;&amp; !id.isEmpty())
            .distinct()
            .collect(java.util.stream.Collectors.toList());

        if (userIds.isEmpty()) return;

        // TODO: 批量查询用户名称（替换为你的实际查询逻辑）
        Map&lt;String, String&gt; userNameMap = new java.util.HashMap&lt;&gt;();
        // userNameMap = userMapper.selectNamesByIds(userIds);

        // 将查询结果回填到 extData
        for (EntityDataDTO record : records) {
            Object userId = record.getData() != null ? record.getData().get("userId") : null;
            if (userId != null) {
                String name = userNameMap.get(userId.toString());
                if (name != null) {
                    if (record.getExtData() == null) {
                        record.setExtData(new java.util.HashMap&lt;&gt;());
                    }
                    record.getExtData().put("userName", name);
                }
            }
        }
    }
}</code></pre>
                </el-card>
              </div>

              <div id="section0-1" class="subsection">
                <div class="subsection-title">2. 接口说明</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="danger">Java</el-tag>
                      <span class="code-title">ListFieldDataProvider.java</span>
                    </div>
                  </template>
                  <pre class="code-block"><code>public interface ListFieldDataProvider {

    /**
     * 返回支持的数据源类型
     * 可选值：ENTITY_FIELD / REFERENCE / AGGREGATE / CUSTOM_PROVIDER
     */
    String getDataSourceType();

    /**
     * 补充自定义列数据
     * @param records    基础查询结果列表
     * @param fields     当前需要补充的字段配置列表
     * @param context    上下文参数 {entityCode, listKey, listConfigId}
     */
    void enrich(List&lt;EntityDataDTO&gt; records, List&lt;EntityListField&gt; fields,
                Map&lt;String, Object&gt; context);
}</code></pre>
                </el-card>
              </div>

              <div id="section0-2" class="subsection">
                <div class="subsection-title">3. 注意事项</div>
                <div class="section-tips">
                  <ul>
                    <li>实现类必须标记 <code>@Component</code>，Spring 会自动扫描并注册</li>
                    <li>补充的数据放入 <code>record.getExtData().put("fieldCode", value)</code>，前端通过 <code>extData.fieldCode</code> 读取</li>
                    <li>建议使用批量查询（IN）避免 N+1 性能问题</li>
                    <li>context 中包含 entityCode、listKey、listConfigId 等上下文信息</li>
                  </ul>
                </div>
              </div>
            </div>
          </section>

          <!-- 二、前端 -->
          <section id="section1" class="doc-section">
            <div class="section-title">二、前端：注册自定义渲染组件</div>
            <div class="section-content">
              <p class="section-intro">
                在 listCellRegistry.js 中注册自定义 Vue 组件，组件会接收 value、row、field、config 四个 props。
              </p>

              <div id="section1-0" class="subsection">
                <div class="subsection-title">1. 创建自定义单元格组件</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="success">Vue</el-tag>
                      <span class="code-title">UserTag.vue</span>
                    </div>
                  </template>
                  <pre class="code-block"><code>&lt;template&gt;
  &lt;el-tag :type="tagType" size="small"&gt;
    &lt;el-icon v-if="showIcon"&gt;&lt;User /&gt;&lt;/el-icon&gt;
    {{ displayValue }}
  &lt;/el-tag&gt;
&lt;/template&gt;

&lt;script setup&gt;
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) }
})

const tagType = computed(() => props.config?.tagType || 'primary')
const showIcon = computed(() => props.config?.showIcon !== false)
const displayValue = computed(() => props.value || '-')
&lt;/script&gt;</code></pre>
                </el-card>
              </div>

              <div id="section1-1" class="subsection">
                <div class="subsection-title">2. 注册组件</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="success">JavaScript</el-tag>
                      <span class="code-title">main.js</span>
                    </div>
                  </template>
                  <pre class="code-block"><code>// 在 src/main.js 或任意初始化文件中
import { registerCellComponent } from '@/utils/listCellRegistry.js'
import UserTag from '@/components/list-cells/UserTag.vue'

registerCellComponent('UserTag', UserTag)</code></pre>
                </el-card>
              </div>

              <div id="section1-2" class="subsection">
                <div class="subsection-title">3. 组件 Props 说明</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small" type="success">JavaScript</el-tag>
                      <span class="code-title">Props 定义</span>
                    </div>
                  </template>
                  <pre class="code-block"><code>// 所有列表单元格组件都会接收以下 props：

props: {
  // 单元格值（从 extData 或 data 中解析出的字段值）
  value: [String, Number, Boolean, Object, Array],
  
  // 整行数据（包含 data、extData、系统字段等）
  row: Object,
  
  // 字段配置（来自 entity_list_field 表）
  field: Object,
  
  // 数据源配置（data_source_config 字段解析后的 JSON 对象）
  config: Object
}</code></pre>
                </el-card>
              </div>

              <div id="section1-3" class="subsection">
                <div class="subsection-title">4. 注意事项</div>
                <div class="section-tips">
                  <ul>
                    <li>组件放在 <code>src/components/list-cells/</code> 目录下，命名建议以 Cell 结尾</li>
                    <li><code>registerCellComponent</code> 可在 main.js 中批量注册所有自定义组件</li>
                    <li>通过 <code>field.dataSourceConfig</code> 可以读取后端配置传递的额外参数</li>
                    <li>如果 value 为空，建议显示 "-" 占位</li>
                  </ul>
                </div>
              </div>
            </div>
          </section>

          <!-- 三、配置 -->
          <section id="section2" class="doc-section">
            <div class="section-title">三、列表配置设计页面操作</div>
            <div class="section-content">
              <p class="section-intro">
                在列表配置设计页面中，为字段配置数据源类型和渲染组件。
              </p>

              <div id="section2-0" class="subsection">
                <div class="subsection-title">1. 配置步骤</div>
                <el-card shadow="never" class="code-block-card">
                  <template #header>
                    <div class="code-header">
                      <el-tag size="small">步骤</el-tag>
                      <span class="code-title">操作流程</span>
                    </div>
                  </template>
                  <pre class="code-block"><code>1. 进入「实体管理」→ 选择实体 →「列表配置」→「设计」

2. 在字段配置表格中找到目标字段

3. 设置「数据源」列：
   - ENTITY_FIELD    → 实体表字段（默认行为）
   - REFERENCE       → 关联查询（通过外键关联其他表）
   - AGGREGATE       → 聚合统计（COUNT / SUM / AVG）
   - CUSTOM_PROVIDER → 自定义处理器（通过代码实现）

4. 设置「渲染组件」列（可选）：
   - DefaultText    → 默认文本显示
   - StatusBadge    → 状态标签（支持颜色映射）
   - DateFormatter  → 日期格式化
   - 或选择你自定义注册的组件

5. 保存配置后，在实体数据列表页面即可看到效果</code></pre>
                </el-card>
              </div>

              <div id="section2-1" class="subsection">
                <div class="subsection-title">2. 注意事项</div>
                <div class="section-tips">
                  <ul>
                    <li>数据源为 <code>ENTITY_FIELD</code> 时，renderComponent 也可用于改变渲染样式</li>
                    <li><code>CUSTOM_PROVIDER</code> 需要在后端有对应的 ListFieldDataProvider 实现</li>
                    <li>修改配置后需要刷新列表页面才能看到效果</li>
                  </ul>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>

      <!-- 右侧目录 -->
      <div class="toc-sidebar" :class="{ collapsed: tocCollapsed }">
        <!-- 展开状态：目录卡片 -->
        <div v-show="!tocCollapsed" class="toc-card">
          <div class="toc-header">
            <span class="toc-title">目录</span>
            <el-icon class="toc-close" @click="tocCollapsed = true"><ArrowRight /></el-icon>
          </div>
          <div class="toc-list">
            <template v-for="item in tocItems" :key="item.id">
              <div
                v-if="item.level === 1 || isTocExpanded(item.parentId)"
                :class="['toc-item', 'toc-level-' + item.level, { active: activeTocId === item.id }]"
              >
                <!-- 一级标题：箭头控制展开，文字控制跳转 -->
                <template v-if="item.level === 1">
                  <span class="toc-arrow" @click.stop="toggleTocExpand(item.id)">
                    <el-icon v-if="isTocExpanded(item.id)"><ArrowDown /></el-icon>
                    <el-icon v-else><ArrowRight /></el-icon>
                  </span>
                  <span class="toc-text" @click="onTocItemClick(item)">{{ item.title }}</span>
                </template>
                <!-- 二级标题：直接跳转 -->
                <template v-else>
                  <span class="toc-text" @click="onTocItemClick(item)">{{ item.title }}</span>
                </template>
              </div>
            </template>
          </div>
        </div>

        <!-- 收起状态：竖向标签按钮 -->
        <div v-show="tocCollapsed" class="toc-toggle-btn" @click="tocCollapsed = false">
          <el-icon><ArrowLeft /></el-icon>
          <span class="toggle-text">目录</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowRight, ArrowDown, ArrowLeft } from '@element-plus/icons-vue'

const activeTocId = ref('section0')
const tocCollapsed = ref(false)
const tocExpanded = ref({
  'section0': true,
  'section1': true,
  'section2': true
})

let scrollContainer = null

const tocItems = [
  { id: 'section0', title: '一、后端：实现 ListFieldDataProvider 接口', level: 1, parentId: null },
  { id: 'section0-0', title: '1. 创建自定义数据提供者', level: 2, parentId: 'section0' },
  { id: 'section0-1', title: '2. 接口说明', level: 2, parentId: 'section0' },
  { id: 'section0-2', title: '3. 注意事项', level: 2, parentId: 'section0' },
  { id: 'section1', title: '二、前端：注册自定义渲染组件', level: 1, parentId: null },
  { id: 'section1-0', title: '1. 创建自定义单元格组件', level: 2, parentId: 'section1' },
  { id: 'section1-1', title: '2. 注册组件', level: 2, parentId: 'section1' },
  { id: 'section1-2', title: '3. 组件 Props 说明', level: 2, parentId: 'section1' },
  { id: 'section1-3', title: '4. 注意事项', level: 2, parentId: 'section1' },
  { id: 'section2', title: '三、列表配置设计页面操作', level: 1, parentId: null },
  { id: 'section2-0', title: '1. 配置步骤', level: 2, parentId: 'section2' },
  { id: 'section2-1', title: '2. 注意事项', level: 2, parentId: 'section2' }
]

function isTocExpanded(id) {
  return tocExpanded.value[id] !== false
}

function toggleTocExpand(id) {
  tocExpanded.value[id] = !isTocExpanded(id)
}

function onTocItemClick(item) {
  // 如果是子标题，确保父级展开
  if (item.parentId) {
    tocExpanded.value[item.parentId] = true
  }
  scrollToToc(item.id)
}

function getScrollContainer() {
  if (scrollContainer) return scrollContainer
  // Layout.vue 中的 el-main 是滚动容器
  scrollContainer = document.querySelector('.layout-container .el-main')
  return scrollContainer
}

function scrollToToc(id) {
  const el = document.getElementById(id)
  const container = getScrollContainer()
  if (el && container) {
    const offset = 20 // 顶部留出空间
    const containerRect = container.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const top = elRect.top - containerRect.top + container.scrollTop - offset
    container.scrollTo({ top, behavior: 'smooth' })
  }
}

function onScroll() {
  const container = getScrollContainer()
  if (!container) return

  const containerRect = container.getBoundingClientRect()

  // 方式1：找视口内最上方的元素
  let bestMatch = null
  let bestTop = Infinity

  for (const item of tocItems) {
    const el = document.getElementById(item.id)
    if (el) {
      const rect = el.getBoundingClientRect()
      // 元素在视口内（含上下边界）
      if (rect.top >= containerRect.top && rect.top <= containerRect.bottom) {
        if (rect.top < bestTop) {
          bestTop = rect.top
          bestMatch = item.id
        }
      }
    }
  }

  // 方式2：如果视口内没有标题，找最后一个滚出视口顶部的元素
  if (!bestMatch) {
    for (let i = tocItems.length - 1; i >= 0; i--) {
      const el = document.getElementById(tocItems[i].id)
      if (el) {
        const top = el.getBoundingClientRect().top
        if (top <= containerRect.top + 10) {
          bestMatch = tocItems[i].id
          break
        }
      }
    }
  }

  // 兜底：选中第一个
  if (!bestMatch) {
    bestMatch = tocItems[0].id
  }

  activeTocId.value = bestMatch
  const item = tocItems.find(i => i.id === bestMatch)
  if (item?.parentId) {
    tocExpanded.value[item.parentId] = true
  }
}

onMounted(() => {
  const container = getScrollContainer()
  if (container) {
    container.addEventListener('scroll', onScroll, { passive: true })
  }
  // 延迟执行一次，确保 DOM 已渲染
  setTimeout(onScroll, 200)
})

onUnmounted(() => {
  if (scrollContainer) {
    scrollContainer.removeEventListener('scroll', onScroll)
  }
})
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

.content-layout {
  display: flex;
  gap: 20px;

  .main-content {
    flex: 1;
    min-width: 0;
  }
}

// 右侧目录
.toc-sidebar {
  width: 260px;
  flex-shrink: 0;
  transition: width 0.25s ease;

  &.collapsed {
    width: 36px;
  }

  .toc-card {
    position: sticky;
    top: 0;
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 12px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.06);
    max-height: calc(100vh - 100px);
    overflow-y: auto;

    .toc-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      padding-bottom: 8px;
      border-bottom: 1px solid #ebeef5;

      .toc-title {
        font-size: 15px;
        font-weight: 600;
        color: #303133;
      }

      .toc-close {
        cursor: pointer;
        color: #909399;
        font-size: 14px;
        padding: 4px;
        border-radius: 4px;
        transition: all 0.2s;

        &:hover {
          color: #409eff;
          background-color: #ecf5ff;
        }
      }
    }

    .toc-list {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .toc-item {
      display: flex;
      align-items: flex-start;
      padding: 5px 8px;
      border-radius: 4px;
      font-size: 13px;
      color: #606266;
      cursor: pointer;
      transition: all 0.2s;
      line-height: 1.5;

      &:hover {
        color: #409eff;
        background-color: #ecf5ff;
      }

      &.active {
        color: #409eff;
        background-color: #ecf5ff;
        font-weight: 500;
      }

      .toc-arrow {
        display: inline-flex;
        align-items: center;
        margin-right: 4px;
        margin-top: 2px;
        font-size: 12px;
        flex-shrink: 0;
        width: 16px;
        height: 16px;
        justify-content: center;
        border-radius: 2px;

        &:hover {
          background-color: #d9ecff;
        }
      }

      .toc-text {
        flex: 1;
      }

      &.toc-level-1 {
        font-weight: 500;
        font-size: 13px;
      }

      &.toc-level-2 {
        padding-left: 28px;
        font-size: 12px;
        color: #909399;

        &.active {
          color: #409eff;
        }

        &:hover {
          color: #409eff;
        }
      }
    }
  }

  .toc-toggle-btn {
    position: sticky;
    top: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 80px;
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    color: #909399;
    transition: all 0.2s;
    writing-mode: vertical-rl;
    letter-spacing: 2px;
    font-size: 12px;

    &:hover {
      color: #409eff;
      border-color: #409eff;
    }

    .toggle-text {
      font-size: 12px;
    }
  }
}

// 响应式：小屏幕隐藏目录
@media (max-width: 1024px) {
  .toc-sidebar {
    display: none;
  }
}

// 文档区块
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
}
</style>
