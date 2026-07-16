<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>列表字段扩展</h2>
        <p>动态列、虚拟列、数据提供者和单元格组件的完整扩展契约。</p>
      </div>
      <el-tag type="success">配置优先，代码兜底</el-tag>
    </div>

    <div class="guide-layout">
      <main class="guide-content">
        <el-alert
          title="不要为简单格式化编写自定义页面。实体字段、字段组合模板、结构化查询和内置渲染器已经覆盖大多数列表需求。"
          type="info"
          :closable="false"
          show-icon
        />

        <section id="levels" class="guide-section">
          <h3>1. 扩展层级怎么选</h3>
          <el-table :data="extensionLevels" border size="small">
            <el-table-column prop="level" label="层级" width="150" />
            <el-table-column prop="useCase" label="适用场景" min-width="240" />
            <el-table-column prop="implementation" label="实现方式" min-width="260" />
          </el-table>
        </section>

        <section id="field-config" class="guide-section">
          <h3>2. 动态列配置模型</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="fieldCode">运行时唯一键；实体字段使用原字段编码，虚拟列使用自定义安全编码。</el-descriptions-item>
            <el-descriptions-item label="dataSourceType">`ENTITY_FIELD` 或已注册的 `ListFieldDataProvider` 编码。</el-descriptions-item>
            <el-descriptions-item label="dataSourceConfig">数据提供者参数，按提供者声明的 `configSchema` 可视化编辑。</el-descriptions-item>
            <el-descriptions-item label="renderComponent">单元格组件注册名；留空时自动选择默认文本或字段类型渲染。</el-descriptions-item>
            <el-descriptions-item label="renderConfig">单元格组件参数，不再与数据源参数混用。</el-descriptions-item>
            <el-descriptions-item label="queryType">支持 EQ、NE、LIKE、NOT_LIKE、GT、GE、LT、LE、BETWEEN、IN、NOT_IN、EMPTY、NOT_EMPTY。</el-descriptions-item>
            <el-descriptions-item label="queryConfig">查询组件、占位提示和默认值。</el-descriptions-item>
            <el-descriptions-item label="columnConfig">固定位置、最小宽度、溢出提示等列展示配置。</el-descriptions-item>
          </el-descriptions>
          <div class="tips">
            <p>查询字段不要求同时显示在列表中。虚拟查询字段会先完成扩展值计算，再由后端结构化条件过滤，不会把虚拟字段拼进实体 SQL。</p>
          </div>
        </section>

        <section id="provider" class="guide-section">
          <h3>3. 后端数据提供者</h3>
          <p>复杂关联、聚合或业务计算通过 Spring Bean 注册。编码必须稳定且唯一，配置保存时后端会校验数据源是否存在、参数是否完整、是否支持虚拟列和查询。</p>
          <CodeCard title="CustomerLevelProvider.java" language="Java">
            <pre v-pre><code>@Component
public class CustomerLevelProvider implements ListFieldDataProvider {
    @Override
    public String getDataSourceType() {
        return "CUSTOMER_LEVEL";
    }

    @Override
    public String getDisplayName() {
        return "客户等级";
    }

    @Override
    public String getDescription() {
        return "根据客户ID批量查询等级，避免逐行请求。";
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public List&lt;Map&lt;String, Object&gt;&gt; getConfigSchema() {
        return List.of(Map.of(
            "key", "customerField",
            "label", "客户字段",
            "type", "text",
            "required", true
        ));
    }

    @Override
    public void enrich(List&lt;EntityDataDTO&gt; records,
                       List&lt;EntityListField&gt; fields,
                       Map&lt;String, Object&gt; context) {
        // 1. 汇总当前页/当前结果集中的客户ID
        // 2. 一次批量查询客户等级
        // 3. 写入 record.extData[field.getFieldCode()]
    }
}</code></pre>
          </CodeCard>
          <el-alert
            title="查询条件命中未注册的数据源时后端拒绝请求，避免扩展查询失效后返回未过滤数据。"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>

        <section id="provider-context" class="guide-section">
          <h3>4. 提供者上下文与约束</h3>
          <el-table :data="providerContext" border size="small">
            <el-table-column prop="key" label="上下文键" width="180" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <ul class="check-list">
            <li>使用批量查询，禁止在每条记录中单独访问数据库或远程接口。</li>
            <li>扩展值写入 `extData`，不要覆盖实体原始 `data`。</li>
            <li>外部调用必须设置超时、限流和降级；敏感信息不能写入配置 JSON。</li>
            <li>自定义查询仍受实体数据权限约束，提供者只能处理已授权记录。</li>
          </ul>
        </section>

        <section id="cell" class="guide-section">
          <h3>5. 前端单元格组件</h3>
          <p>组件注册时同时声明名称、说明和参数 Schema，设计器会自动生成参数表单，不需要管理员手写 JSON。</p>
          <CodeCard title="extension.ts" language="TypeScript">
            <pre v-pre><code>import { registerCellComponent } from '@/utils/listCellRegistry'
import RiskBadgeCell from './RiskBadgeCell.vue'

registerCellComponent('RiskBadgeCell', RiskBadgeCell, {
  label: '风险等级',
  description: '按阈值显示风险标签',
  configSchema: [
    { key: 'warningAt', label: '预警阈值', type: 'number', required: true },
    { key: 'dangerAt', label: '高危阈值', type: 'number', required: true }
  ]
})</code></pre>
          </CodeCard>
          <CodeCard title="RiskBadgeCell.vue" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
const props = defineProps({
  value: [String, Number, Boolean, Object, Array],
  row: Object,
  field: Object,
  config: Object,
  context: Object
})
&lt;/script&gt;</code></pre>
          </CodeCard>
        </section>

        <section id="built-in" class="guide-section">
          <h3>6. 内置能力</h3>
          <el-table :data="builtIns" border size="small">
            <el-table-column prop="type" label="类型" width="180" />
            <el-table-column prop="capability" label="能力" />
          </el-table>
          <p class="muted">`FIELD_TEMPLATE` 仅替换 `${fieldCode}` 占位符，不执行 JavaScript、Groovy 或 SQL，可用于编号+名称、人员+部门等组合列。</p>
        </section>

        <section id="demo" class="guide-section">
          <h3>7. 可运行 Demo</h3>
          <p>仓库已经提供真实注册示例，不需要从文档片段重新拼装：</p>
          <ul class="check-list">
            <li>`src/demo/list-fields/DemoRiskProgressCell.vue`：读取 `value / row / field / config / context`，展示风险进度和等级。</li>
            <li>`src/demo/index.js`：以 `DemoRiskProgressCell` 注册组件，并声明数值字段类型与四项可视化参数。</li>
            <li>`scripts/real-dynamic-extension-demo.mjs`：创建实体、动态列、定制列表、定制表单和流程，验证配置真实生效。</li>
            <li>执行 `npm run test:demo:real`；最近一次结果保存在 `docs/dynamic-extension-demo/latest.json`。</li>
          </ul>
          <el-alert
            title="单元格展示参数请保存到 renderConfig；dataSourceConfig 只描述数据如何产生。运行时仅为历史配置兼容才回退读取 dataSourceConfig。"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>

        <section id="acceptance" class="guide-section">
          <h3>8. 验收清单</h3>
          <ul class="check-list">
            <li>虚拟列能显示、排序位置正确，空值有明确占位。</li>
            <li>隐藏查询项仍可查询，虚拟查询不会触发未知数据库列错误。</li>
            <li>未注册数据源不能保存；历史未注册展示字段不会绕过查询过滤。</li>
            <li>自定义组件不存在时回退默认文本，不影响列表基本访问。</li>
            <li>数据权限、功能权限和行操作能力仍由后端统一计算。</li>
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
  { id: 'levels', label: '扩展层级' },
  { id: 'field-config', label: '动态列配置' },
  { id: 'provider', label: '后端提供者' },
  { id: 'provider-context', label: '上下文与约束' },
  { id: 'cell', label: '单元格组件' },
  { id: 'built-in', label: '内置能力' },
  { id: 'demo', label: '可运行 Demo' },
  { id: 'acceptance', label: '验收清单' }
]

const extensionLevels = [
  { level: '实体字段', useCase: '直接显示、查询实体字段', implementation: '选择 ENTITY_FIELD，配置列宽、查询方式和内置渲染器' },
  { level: '声明式虚拟列', useCase: '字段拼接、轻量派生值', implementation: '添加虚拟列，选择 FIELD_TEMPLATE 等可配置数据源' },
  { level: '自定义数据提供者', useCase: '关联、聚合、远程业务数据', implementation: '实现 ListFieldDataProvider 并声明配置 Schema' },
  { level: '自定义单元格', useCase: '进度、标签、图片、复杂交互展示', implementation: 'registerCellComponent 注册 Vue 组件' },
  { level: '整页自定义列表', useCase: '卡片、看板、树、地图等非表格布局', implementation: '使用自定义列表组件；详见“自定义列表组件”' }
]

const providerContext = [
  { key: 'entityCode', meaning: '当前实体编码' },
  { key: 'listKey', meaning: '当前列表标识' },
  { key: 'listConfigId', meaning: '当前列表配置ID' },
  { key: 'userId / userName', meaning: '当前登录用户，用于业务上下文；不能替代数据权限校验' }
]

const builtIns = [
  { type: 'ENTITY_FIELD', capability: '读取实体系统字段或自定义字段，支持数据库查询条件' },
  { type: 'FIELD_TEMPLATE', capability: '安全字段占位符组合，支持虚拟列和结构化查询' },
  { type: 'DefaultText', capability: '默认文本与空值占位' },
  { type: 'StatusBadge', capability: '状态文本和颜色映射' },
  { type: 'DateFormatter', capability: '安全日期模板格式化' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
