<template>
  <div class="guide-page">
    <div class="guide-header">
      <div>
        <h2>列表字段扩展 2</h2>
        <p>一列数据怎么来、格子怎么画。先看总流程，再按需求往下做。</p>
      </div>
      <el-tag type="success">先总后分</el-tag>
    </div>

    <div class="guide-layout">
      <main class="guide-content">
        <section id="overview" class="guide-section">
          <h3>1. 先看全貌</h3>
          <p>列表每一列只做两件事：<strong>出数</strong>和<strong>显示</strong>。扩展时先想清楚你改哪一头，还是两头都改。</p>
          <el-table :data="overviewRows" border size="small">
            <el-table-column prop="part" label="这一头" width="100" />
            <el-table-column prop="what" label="要做什么" min-width="220" />
            <el-table-column prop="how" label="怎么做" min-width="280" />
          </el-table>
          <p>运行时顺序也是这两步：后端先查出当前页，再按列的数据源补 <code>extData</code>；前端按 <code>renderComponent</code> 画格子，<code>value</code> 的取值顺序是 <code>extData &gt; data &gt; 行顶层</code>。</p>
          <p>按需求选路径：</p>
          <ol class="check-list">
            <li>字段已经有值，只想换样子 → 第 2 节，只写一个单元格组件。</li>
            <li>要把已有字段拼成一列展示文本 → 第 3 节，设计器里配模板，不用写代码。</li>
            <li>这一列的值库里没有 → 第 4 节，写 Provider 往 <code>extData</code> 里补。</li>
            <li>自己出数，还要画进度条、还能当查询条件 → 第 5 节，把 2 和 4 叠在同一列。</li>
            <li>设计器每一项都填了、组件里要把 props 用全 → 第 6 节，看一次请求里实际长什么样。</li>
          </ol>
        </section>

        <section id="cell" class="guide-section">
          <h3>2. 换显示：写一个单元格组件</h3>
          <p>平台已经把这一格的值和行数据传给你。组件就是普通 Vue 组件，内部怎么写 computed、怎么拆子组件、怎么用 Element Plus，都是你的事。平台只约定五个入参名字，别改。</p>

          <h4>2.1 要做什么</h4>
          <ol class="check-list">
            <li>写一个 Vue 组件，接收 <code>value / row / field / config / context</code>。</li>
            <li>启动时 <code>registerCellComponent(名字, 组件, 元数据)</code>。名字是设计器里的选项值，稳定后不要改。</li>
            <li>打开实体列表设计 → 字段配置 → 该列「设置」→「数据与显示」→「渲染组件」选中它。</li>
            <li>元数据里的 <code>configSchema</code> 会自动变成参数表单，填完写进这一列的 <code>renderConfig</code>。</li>
            <li>点「保存当前列」。提示是「当前列已保存，尚未发布」。再发布列表，运行时才看得到。</li>
          </ol>

          <h4>2.2 平台传进来的五个 props</h4>
          <el-table :data="cellProps" border size="small">
            <el-table-column prop="name" label="prop" width="110" />
            <el-table-column prop="what" label="是什么" min-width="240" />
            <el-table-column prop="use" label="你怎么用" min-width="260" />
          </el-table>
          <p><code>value</code> 够用就只用 <code>value</code>。要拿同一行别的字段、原始 ID、点一下刷新列表，再去翻 <code>row</code> 和 <code>context</code>。组件里可以继续加自己的 computed、方法、样式，不需要向平台再注册一遍。</p>

          <h4>2.3 从最小组件抄起</h4>
          <p>仓库里能跑的例子是 <code>src/demo/list-fields/DemoRiskProgressCell.vue</code>。开发环境启动后，渲染组件下拉里会有「Demo·风险进度」。自己做业务组件时，复制一份改名字即可。</p>
          <CodeCard title="单元格组件（带注释，可直接改）" language="Vue">
            <pre v-pre><code>&lt;template&gt;
  &lt;!-- 平台只负责把组件挂到格子里，模板怎么排是组件自己的事 --&gt;
  &lt;div class="risk-cell"&gt;
    &lt;el-progress
      :percentage="percentage"
      :status="progressStatus"
      :stroke-width="10"
      :show-text="config.showText !== false"
    /&gt;
    &lt;el-tag v-if="config.showLevel !== false" :type="tagType" size="small"&gt;
      {{ levelText }}
    &lt;/el-tag&gt;
  &lt;/div&gt;
&lt;/template&gt;

&lt;script setup&gt;
import { computed } from 'vue'

// 这五个名字是 ListCellRenderer 传下来的，缺省也能跑，但不要改名
const props = defineProps({
  value: { type: [String, Number], default: 0 },   // 这一格的展示值
  row: { type: Object, default: () => ({}) },      // 整行，需要旁路字段时再用
  field: { type: Object, default: () => ({}) },    // 列配置
  config: { type: Object, default: () => ({}) },   // 设计器里填的 renderConfig
  context: { type: Object, default: () => ({}) }   // entityCode、refresh 等运行时上下文
})

// 显示逻辑全部放组件内部。阈值来自 config，管理员改设计器即可，不用改代码
const percentage = computed(() => {
  const value = Number(props.value)
  if (Number.isNaN(value)) return 0
  return Math.min(100, Math.max(0, value))
})
const warningAt = computed(() => Number(props.config.warningAt ?? 40))
const dangerAt = computed(() => Number(props.config.dangerAt ?? 70))
&lt;/script&gt;</code></pre>
          </CodeCard>

          <h4>2.4 注册：让设计器能选到</h4>
          <p>业务代码放在应用启动时调用，和 Demo 开关分开：<code>src/extensions/register.js</code> 里 <code>registerProjectExtensions()</code> 会随应用启动；<code>registerDemoExtensions()</code> 只在开发环境或 <code>VITE_ENABLE_DEMO_EXTENSIONS=true</code> 时执行。</p>
          <CodeCard title="registerCellComponent（对照 src/demo/index.js）" language="JavaScript">
            <pre v-pre><code>import { registerCellComponent } from '@/utils/listCellRegistry'
import RiskProgressCell from './RiskProgressCell.vue'

// 第一个参数是稳定编码，设计器保存的是它，改名后旧列会回退成默认文本
registerCellComponent('RiskProgressCell', RiskProgressCell, {
  label: '风险进度',                 // 设计器下拉显示名
  description: '按阈值显示进度和等级',
  // 适用实体：不写、[]、['*'] 都是全部实体
  // 只给报销单和合同用时写成 ['expense', 'contract']
  supportedEntityCodes: ['expense'],
  // 提示更适合哪些实体字段类型，不拦运行时
  supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'],
  // 每一项会变成「数据与显示」里的表单，保存后进入 field.renderConfig
  configSchema: [
    { key: 'warningAt', label: '关注阈值', type: 'number', min: 0, max: 100, defaultValue: 40 },
    { key: 'dangerAt', label: '高危阈值', type: 'number', min: 0, max: 100, defaultValue: 70 },
    { key: 'showText', label: '显示百分比', type: 'boolean', defaultValue: true },
    { key: 'showLevel', label: '显示风险等级', type: 'boolean', defaultValue: true }
  ]
})</code></pre>
          </CodeCard>
          <p><code>configSchema</code> 的 <code>type</code> 设计器认识这些：<code>text</code>、<code>textarea</code>、<code>number</code>、<code>boolean</code>、<code>select</code>、<code>json</code>。组件里用 <code>props.config.xxx</code> 读对应 <code>key</code>。显示参数写 <code>renderConfig</code>，不要塞进数据源配置；运行时优先读 <code>field.renderConfig</code>，没有才回退 <code>dataSourceConfig</code>。</p>
          <p>组件没注册时，格子会静默走内置 <code>DefaultText</code>，页面不会报红。自己测的时候先看下拉里有没有你的显示名。</p>

          <h4 id="scope">2.5 限定用在哪个实体</h4>
          <p>扩展多了，每个实体的「渲染组件」「字段数据源」下拉都会很长。注册时用 <code>supportedEntityCodes</code> 收窄范围：</p>
          <el-table :data="entityScopeRows" border size="small">
            <el-table-column prop="write" label="怎么写" min-width="220" />
            <el-table-column prop="effect" label="设计器里怎样" min-width="280" />
          </el-table>
          <p>这只过滤下拉。已经保存到列上的组件和数据源，换实体后仍会渲染、仍会补数；打开该列时，当前选中项即使不在范围内也会留在下拉里，避免配置丢了。</p>
          <CodeCard title="三种范围写法" language="JavaScript">
            <pre v-pre><code>// 1. 全部实体：不写这个字段，或写空数组，或写 ['*']
registerCellComponent('PlainTextCell', PlainTextCell, {
  label: '普通文本'
  // supportedEntityCodes 省略 = 每个实体的列表都能选
})

// 2. 只给一个实体
registerCellComponent('ExpenseRiskCell', ExpenseRiskCell, {
  label: '报销风险',
  supportedEntityCodes: ['expense']
})

// 3. 给几个实体
registerCellComponent('AmountBarCell', AmountBarCell, {
  label: '金额条',
  supportedEntityCodes: ['expense', 'contract', 'order']
})</code></pre>
          </CodeCard>
        </section>

        <section id="template" class="guide-section">
          <h3>3. 拼一列：不用写代码</h3>
          <p>只要把当前行已有字段拼成一段文本，用内置数据源 <code>FIELD_TEMPLATE</code>（设计器里叫「字段组合模板」）。</p>
          <ol class="check-list">
            <li>实体 → 列表设计 → 字段配置 → <strong>添加虚拟列</strong>。</li>
            <li>改名称，比如「摘要」。编码预填 <code>virtual_时间戳</code>，可改成 <code>summary</code>。</li>
            <li>「设置」→「数据与显示」→ 字段数据源选「字段组合模板」。</li>
            <li>组合模板填 <code v-pre>${dataNo} - ${name}</code>。占位符是 <code v-pre>${字段编码}</code>，字段编码以字母开头，只做替换，不跑脚本。</li>
            <li>空值文本配在单元格 <code>renderConfig.emptyText</code>，默认 <code>-</code>。</li>
            <li>保存当前列，发布列表。</li>
          </ol>
          <p>后端 <code>TemplateListFieldDataProvider</code> 渲染后写入 <code>record.extData[字段编码]</code>。占位符取值顺序：<code>extData</code> → <code>data</code> → 系统字段（<code>id / dataNo / name / title / status / submitterName</code>）。</p>
          <p>这个数据源 <code>supportsQuery()</code> 为 true，可以勾「查询」。勾上之后，平台先补值，再在内存里按列的 <code>queryType</code> 过滤，不会把虚拟列写进实体 SQL。</p>
        </section>

        <section id="provider" class="guide-section">
          <h3>4. 自己出数：写一个 Provider</h3>
          <p>这一列的值不是实体字段，也不只是拼接，就实现 <code>ListFieldDataProvider</code>。类上加 <code>@Component</code>，启动时 <code>ListFieldDataProviderRegistry</code> 会按 <code>getDataSourceType()</code> 收走。</p>
          <p>仓库对照：<code>ProjectCustomListFieldDataProvider</code>，类型 <code>PROJECT_CUSTOM_FIELD</code>，设计器显示名「项目自定义日志字段」。</p>

          <h4>4.1 要做什么</h4>
          <ol class="check-list">
            <li>实现接口，<code>getDataSourceType()</code> 用大写编码，规则是 <code>[A-Z][A-Z0-9_]{1,63}</code>，重复会启动失败。</li>
            <li>只给部分实体用时，覆盖 <code>getSupportedEntityCodes()</code>，例如 <code>List.of("expense")</code>。不覆盖则全部实体都能选。</li>
            <li>在 <code>enrich</code> 里对当前页批量补数，写入 <code>record.getExtData().put(列编码, 值)</code>。</li>
            <li>需要管理员可配的参数，用 <code>getConfigSchema()</code>，保存时进这一列的 <code>dataSourceConfig</code>。</li>
            <li>重启后端后，列表设计里添加虚拟列，字段数据源选你的显示名，填参数，保存并发布。</li>
          </ol>
          <p>实体字段的数据源锁死为 <code>ENTITY_FIELD</code>。要自己出数必须「添加虚拟列」。渲染组件实体字段和虚拟列都能选。</p>

          <h4>4.2 带注释的最小实现</h4>
          <CodeCard title="ListFieldDataProvider（对照 ProjectCustomListFieldDataProvider）" language="Java">
            <pre v-pre><code>@Component
public class CustomerLevelProvider implements ListFieldDataProvider {

    // 设计器保存的是这个编码，改名后旧列会找不到数据源
    @Override
    public String getDataSourceType() {
        return "CUSTOMER_LEVEL";
    }

    @Override
    public String getDisplayName() {
        return "客户等级"; // 下拉里看到的名字
    }

    // 空或不写 = 全部实体；只给报销单用就返回 List.of("expense")
    @Override
    public List&lt;String&gt; getSupportedEntityCodes() {
        return List.of("expense");
    }

    // 需要当查询条件时覆盖为 true，设计器里的「查询」才能勾
    @Override
    public boolean supportsQuery() {
        return false;
    }

    // 每一项出现在列的「数据来源」表单里，保存进 dataSourceConfig
    @Override
    public List&lt;Map&lt;String, Object&gt;&gt; getConfigSchema() {
        return List.of(Map.of(
            "key", "customerField",
            "label", "客户字段",
            "type", "text",
            "required", true
        ));
    }

    /**
     * records 是已经按数据权限查出来的当前页。
     * fields 只包含数据源类型等于本 Provider 的列。
     * 失败会让整次列表查询失败，不要吞掉异常后返回半截数据。
     */
    @Override
    public void enrich(
            List&lt;EntityDataDTO&gt; records,
            List&lt;EntityListField&gt; fields,
            Map&lt;String, Object&gt; context) {
        // 先抽出本页要用的 ID，一次查完，不要在 for 记录里打远程
        // 值写 extData。列表读格子时优先看 extData，不要改 record.data
        for (EntityListField field : fields) {
            for (EntityDataDTO record : records) {
                if (record.getExtData() == null) {
                    record.setExtData(new HashMap&lt;&gt;());
                }
                record.getExtData().put(field.getFieldCode(), "VIP");
            }
        }
    }
}</code></pre>
          </CodeCard>

          <h4>4.3 enrich 的 context</h4>
          <p>目前放入的键如下。列表已经查完再调用你，你处理的是当前页记录。</p>
          <el-table :data="contextRows" border size="small">
            <el-table-column prop="key" label="key" width="160" />
            <el-table-column prop="meaning" label="含义" />
          </el-table>
          <p>设计器下拉来自 <code>GET /api/entity-list-config/extension-options</code>。没出现你的显示名，先看后端有没有启动起来、编码是否合法。</p>
        </section>

        <section id="combine" class="guide-section">
          <h3>5. 同一列：出数 + 显示 + 查询</h3>
          <p>出数和显示是同一列上的两个配置，不是两条规则。虚拟列选你的 Provider，渲染组件选你的单元格，阈值进 <code>renderConfig</code>，数据参数进 <code>dataSourceConfig</code>。</p>
          <p>要进查询区：</p>
          <ol class="check-list">
            <li>Provider 的 <code>supportsQuery()</code> 返回 true，否则「查询」复选框是灰的。</li>
            <li>勾上「查询」。即使不勾「列表」，只要勾了查询，后端仍会先 <code>enrich</code> 再过滤。</li>
            <li>过滤在 <code>ListFieldConditionEvaluator</code> 里做。操作符用列上的 <code>queryType</code>，请求里也可以带 <code>{fieldCode}_op</code>。范围用 <code>{fieldCode}_start</code> / <code>{fieldCode}_end</code>。</li>
          </ol>
          <p>操作符：<code>EQ</code>、<code>NE</code>、<code>LIKE</code>/<code>CONTAINS</code>、<code>NOT_LIKE</code>/<code>NOT_CONTAINS</code>、<code>GT</code>、<code>GE</code>/<code>GTE</code>、<code>LT</code>、<code>LE</code>/<code>LTE</code>、<code>BETWEEN</code>、<code>IN</code>、<code>NOT_IN</code>、<code>EMPTY</code>/<code>IS_EMPTY</code>、<code>NOT_EMPTY</code>/<code>IS_NOT_EMPTY</code>。未识别的操作符当不匹配。</p>
          <p>数据源没注册却拿这列当查询条件，后端会抛「查询字段的数据源未注册」，避免查着查着条件丢了。</p>
        </section>

        <section id="deep" class="guide-section">
          <h3>6. 深一层：每个属性都填上之后</h3>
          <p>前面几节可以只碰 <code>value</code> 和两三个配置项。管理员把列配满、Provider 也把 <code>extData</code> 写满时，五个 props 会同时带齐。下面按一次真实渲染拆开。</p>

          <h4>6.1 格子实际收到的一份快照</h4>
          <p>假设虚拟列编码 <code>riskScore</code>，数据源补了 82，渲染组件选了风险进度，阈值和查询都配过。组件入参就是这样（JSON 里的函数写成了说明）：</p>
          <CodeCard title="一次渲染时的 props（值来自真实字段，不是示意乱编）" language="JSON">
            <pre v-pre><code>{
  "value": 82,
  "config": {
    "warningAt": 40,
    "dangerAt": 70,
    "showText": true,
    "showLevel": true
  },
  "field": {
    "id": "col_risk_001",
    "listConfigId": "lst_expense_default",
    "fieldId": "virtual_1710000000000",
    "fieldCode": "riskScore",
    "fieldName": "风险",
    "showInList": true,
    "isQuery": true,
    "queryType": "GE",
    "width": 180,
    "align": "left",
    "dataSourceType": "CUSTOMER_LEVEL",
    "dataSourceConfig": "{\"customerField\":\"customerId\"}",
    "renderComponent": "RiskProgressCell",
    "renderConfig": "{\"warningAt\":40,\"dangerAt\":70,\"showText\":true,\"showLevel\":true}",
    "queryConfig": "{\"componentType\":\"number\",\"placeholder\":\"最低风险\",\"defaultValue\":\"\"}",
    "columnConfig": "{\"fixed\":\"\",\"minWidth\":180,\"showOverflowTooltip\":true,\"quickCopy\":true}",
    "revision": 3
  },
  "row": {
    "id": "2038628006255251457",
    "entityCode": "expense",
    "dataNo": "BX-001",
    "title": "差旅报销",
    "name": "差旅报销",
    "status": "PENDING",
    "data": { "amount": 1200, "customerId": "c1" },
    "extData": { "riskScore": 82, "summary": "BX-001 - 差旅报销" },
    "submitterId": "u1",
    "submitterName": "张三",
    "deptId": "dept-1",
    "deptName": "销售部",
    "createdBy": "zhangsan",
    "actionCapabilities": {
      "edit": { "visible": true, "enabled": true, "reason": "" }
    }
  },
  "context": {
    "entityCode": "expense",
    "entityDefinition": { "entityCode": "expense", "entityName": "报销单" },
    "entityStatusMap": { "PENDING": "审批中", "APPROVED": "已通过" },
    "getStatusText": "function(status)",
    "refresh": "function() 重新拉当前列表",
    "refEntityNameMap": { "CUSTOM:customer:c1": "华东客户" }
  }
}</code></pre>
          </CodeCard>
          <p>对照关系：</p>
          <ul class="check-list">
            <li><code>config</code> 是 <code>field.renderConfig</code> 解析后的对象。组件读 <code>config.warningAt</code>，不要自己再 <code>JSON.parse(field.renderConfig)</code>。</li>
            <li><code>value</code> 不是你在组件里算的，是 <code>ListCellRenderer</code> 先跑 <code>formatListFieldValue(row, field)</code> 再传进来。取值顺序 <code>extData &gt; data &gt; 行顶层</code>；字段编码是下划线时还会试驼峰，比如 <code>customer_name</code> 能读到 <code>data.customerName</code>。</li>
            <li>如果 <code>extData</code> 里同时有 <code>riskScore_display</code>，<code>value</code> 会变成那串展示文本，不再是 82。进度条这类组件不要写 <code>riskScore_display</code>，数字会丢。</li>
            <li>引用、多选的选项名在 <code>row.extData.字段Options</code> 或 <code>context.refEntityNameMap</code>。默认文本组件会用它们；你自己画格子时也可以直接读。</li>
            <li><code>field.dataSourceConfig</code> 仍是字符串。出数参数在后端 Provider 里解析，单元格一般不用动它。</li>
          </ul>

          <h4>6.2 五个 props 用全的组件</h4>
          <CodeCard title="把 row / field / context 都用上" language="Vue">
            <pre v-pre><code>&lt;script setup&gt;
import { computed } from 'vue'

// 五个名字仍然不能改。值都填齐时，按需取，不必五个都用。
const props = defineProps({
  value: { type: [String, Number], default: 0 }, // 已经被 formatListFieldValue 处理过
  row: { type: Object, default: () => ({}) },    // data / extData / 系统字段
  field: { type: Object, default: () => ({}) },  // 列配置，含 fieldCode
  config: { type: Object, default: () => ({}) }, // 解析后的 renderConfig
  context: { type: Object, default: () => ({}) } // refresh、refEntityNameMap
})

// value 已经被 formatListFieldValue 处理过；要原始数字时回 extData
const score = computed(() => {
  const raw = props.row?.extData?.[props.field.fieldCode]
  const n = Number(raw ?? props.value)
  return Number.isNaN(n) ? 0 : n
})

// 同一行旁路字段：自定义字段在 row.data，系统字段在行顶层
const customerId = computed(() => props.row?.data?.customerId)
const dataNo = computed(() => props.row?.dataNo)

// 引用显示名：key 是 类型:实体ID:记录ID
const customerName = computed(() => {
  const map = props.context?.refEntityNameMap || {}
  return map[`CUSTOM:customer:${customerId.value}`] || customerId.value || '-'
})

function reloadList() {
  // 改完关联数据后刷新当前列表，平台传入的就是这个函数
  props.context?.refresh?.()
}
&lt;/script&gt;</code></pre>
          </CodeCard>

          <h4>6.3 注册元数据全开</h4>
          <p><code>normalizeExtensionDescriptor</code> 会收下这些字段。没写的给默认值，多写的不会进运行时。</p>
          <CodeCard title="registerCellComponent 元数据全开" language="JavaScript">
            <pre v-pre><code>registerCellComponent('RiskProgressCell', RiskProgressCell, {
  label: '风险进度',
  description: '按阈值显示进度和等级',
  version: 1,                 // 缺省 1，给扩展清单用
  snapshotVersion: 1,         // 缺省 1
  supportedEntityCodes: ['expense'], // 不写或 [] 或 ['*'] = 全部实体
  supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'],
  supportedModes: [],         // 单元格目前不用，保留字段
  capabilities: {},           // 单元格目前不用，保留字段
  configSchema: [
    {
      key: 'warningAt',
      label: '关注阈值',
      type: 'number',
      required: true,
      min: 0,
      max: 100,
      step: 1,
      defaultValue: 40,
      group: 'common',        // 常用区，首屏展开
      order: 10,
      description: '达到该值显示关注'
    },
    {
      key: 'dangerAt',
      label: '高危阈值',
      type: 'number',
      min: 0,
      max: 100,
      defaultValue: 70,
      group: 'common',
      order: 20
    },
    {
      key: 'showText',
      label: '显示百分比',
      type: 'boolean',
      defaultValue: true,
      group: 'common',
      order: 30
    },
    {
      key: 'showLevel',
      label: '显示风险等级',
      type: 'boolean',
      defaultValue: true,
      group: 'common',
      order: 40
    },
    {
      key: 'dangerText',
      label: '高危文案',
      type: 'text',
      defaultValue: '高风险',
      placeholder: '例如 高风险',
      group: 'advanced',      // 高级区，默认折叠
      advanced: true,
      order: 100,
      // 只有打开「显示风险等级」才出现这一项
      visibleWhen: { field: 'showLevel', equals: true }
    }
  ]
})</code></pre>
          </CodeCard>
          <el-table :data="schemaItemRows" border size="small">
            <el-table-column prop="key" label="schema 项" width="140" />
            <el-table-column prop="note" label="设计器怎么用" />
          </el-table>
          <p><code>visibleWhen</code> 还认 <code>notEquals</code>、<code>in</code>、<code>notIn</code>、<code>includes</code>、<code>exists</code>、<code>truthy</code> / <code>falsy</code>，以及 <code>all</code>/<code>and</code>、<code>any</code>/<code>or</code>、<code>not</code>。服务端下发的 schema 不要带可执行函数。</p>

          <h4>6.4 列上四份 JSON 各自管什么</h4>
          <el-table :data="jsonBagRows" border size="small">
            <el-table-column prop="bag" label="字段" width="170" />
            <el-table-column prop="who" label="谁填" width="140" />
            <el-table-column prop="shape" label="配满时的内容" min-width="320" />
          </el-table>
          <CodeCard title="同一列四份配置都写上" language="JSON">
            <pre v-pre><code>{
  "dataSourceConfig": { "customerField": "customerId", "labelPrefix": "等级" },
  "renderConfig": { "warningAt": 40, "dangerAt": 70, "showText": true, "showLevel": true, "dangerText": "高风险" },
  "queryConfig": { "componentType": "number", "placeholder": "最低风险", "defaultValue": "" },
  "columnConfig": { "fixed": "left", "minWidth": 180, "showOverflowTooltip": true, "quickCopy": true }
}</code></pre>
          </CodeCard>
          <p>查询方式本身不在 <code>queryConfig</code> 里，在列字段 <code>queryType</code>。设计器可选：<code>EQ NE LIKE NOT_LIKE GT GE LT LE BETWEEN IN NOT_IN EMPTY NOT_EMPTY</code>。内存过滤里 <code>CONTAINS</code> 等于 <code>LIKE</code>，<code>GTE</code> 等于 <code>GE</code>，请求里也可以带 <code>riskScore_op=GE</code>、<code>riskScore_start</code> / <code>riskScore_end</code>。</p>

          <h4>6.5 Provider 方法全覆盖</h4>
          <p>接口里每个方法都重写时，行为如下。</p>
          <el-table :data="providerFullRows" border size="small">
            <el-table-column prop="method" label="方法" width="240" />
            <el-table-column prop="whenSet" label="你写了之后" />
          </el-table>
          <p>模板列配满时，<code>dataSourceType=FIELD_TEMPLATE</code>，<code>dataSourceConfig</code> 只有 <code>template</code>，例如 <code v-pre>{"template":"${dataNo} - ${name}"}</code>；空值在 <code>renderConfig.emptyText</code>。占位符取值同样是 <code>extData &gt; data &gt; 系统字段</code>。</p>
        </section>

        <section id="designer" class="guide-section">
          <h3>7. 设计器字段落在哪</h3>
          <el-table :data="designerRows" border size="small">
            <el-table-column prop="place" label="你在界面上点的" width="200" />
            <el-table-column prop="field" label="存到哪" width="200" />
            <el-table-column prop="note" label="保存后怎样" min-width="260" />
          </el-table>
        </section>

        <section id="api" class="guide-section">
          <h3>8. 扩展时会用到的接口</h3>
          <h4>前端</h4>
          <el-table :data="frontApis" border size="small">
            <el-table-column prop="api" label="接口" width="280" />
            <el-table-column prop="file" label="在哪" min-width="240" />
            <el-table-column prop="note" label="做什么" min-width="240" />
          </el-table>
          <h4>后端</h4>
          <el-table :data="backApis" border size="small">
            <el-table-column prop="api" label="接口" width="280" />
            <el-table-column prop="file" label="在哪" min-width="240" />
            <el-table-column prop="note" label="做什么" min-width="240" />
          </el-table>
          <h4>ListFieldDataProvider</h4>
          <el-table :data="providerMethods" border size="small">
            <el-table-column prop="method" label="方法" width="260" />
            <el-table-column prop="note" label="默认和约束" />
          </el-table>
        </section>

        <section id="check" class="guide-section">
          <h3>9. 做完自己过一遍</h3>
          <ul class="check-list">
            <li>只保存列、不发布，运行时还是旧快照。</li>
            <li>值写进 <code>extData</code>，不要改 <code>record.data</code>。</li>
            <li><code>enrich</code> 里先收齐 ID 再查，不要按行打远程。</li>
            <li>显示参数进 <code>renderConfig</code>，出数参数进 <code>dataSourceConfig</code>。</li>
            <li>业务注册跟应用启动走，不要只挂在 Demo 开关后面。</li>
            <li>组件名、数据源编码保存后会被列配置引用，不要轻易改。</li>
            <li>进度、计算类组件不要写 <code>字段_display</code>，否则 <code>value</code> 会变成文案。</li>
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
  { id: 'overview', label: '先看全貌' },
  { id: 'cell', label: '换显示' },
  { id: 'scope', label: '适用实体' },
  { id: 'template', label: '模板拼列' },
  { id: 'provider', label: '自己出数' },
  { id: 'combine', label: '叠在同一列' },
  { id: 'deep', label: '属性都填上' },
  { id: 'designer', label: '设计器落点' },
  { id: 'api', label: '接口' },
  { id: 'check', label: '做完检查' }
]

const overviewRows = [
  { part: '出数', what: '这一列每个格子的值从哪来', how: '实体字段直接读；模板拼；或 Provider 写入 extData' },
  { part: '显示', what: '这个值在表格里长什么样', how: 'registerCellComponent 注册 Vue 组件，列上选渲染组件' }
]

const entityScopeRows = [
  { write: '不写 / [] / [\'*\']', effect: '每个实体的列表设计器都能选' },
  { write: '[\'expense\']', effect: '只有实体编码是 expense 的列表能选' },
  { write: '[\'expense\', \'contract\']', effect: '这两个实体能选，其它实体下拉里没有' }
]

const cellProps = [
  { name: 'value', what: '这一格已经算好的展示值', use: '进度、标签、格式化，优先只用它' },
  { name: 'row', what: '当前行 DTO，含 data、extData、id', use: '要旁路字段或原始 ID 时再读' },
  { name: 'field', what: '这一列的配置', use: '看 fieldCode、renderConfig 等' },
  { name: 'config', what: '设计器为这个组件填的参数', use: '阈值、开关、文案；优先来自 renderConfig' },
  { name: 'context', what: '列表运行时上下文', use: 'entityCode、entityDefinition、entityStatusMap、getStatusText、refresh、refEntityNameMap' }
]

const contextRows = [
  { key: 'entityCode', meaning: '当前实体编码' },
  { key: 'listKey', meaning: '当前列表 key' },
  { key: 'listConfigId', meaning: '当前列表配置 ID' },
  { key: 'userId', meaning: '当前登录用户 ID' },
  { key: 'userName', meaning: '当前登录用户名' }
]

const designerRows = [
  { place: '添加虚拟列', field: 'fieldId 以 virtual_ 开头', note: '自己出数用虚拟列；实体字段数据源不能改' },
  { place: '用途：列表 / 查询', field: 'showInList / isQuery', note: '查询能否勾，看数据源 supportsQuery' },
  { place: '字段数据源 + 参数表单', field: 'dataSourceType / dataSourceConfig', note: '选项来自 extension-options' },
  { place: '渲染组件 + 参数表单', field: 'renderComponent / renderConfig', note: '选项来自 getCellComponentOptions()' },
  { place: '保存当前列', field: 'PATCH .../fields/{id}/patch', note: '带 expectedRevision；还要再发布列表' }
]

const frontApis = [
  { api: 'registerCellComponent(name, comp, meta)', file: 'src/utils/listCellRegistry.js', note: '注册单元格。meta 含 label、configSchema、supportedEntityCodes、supportedFieldTypes' },
  { api: 'getCellComponent / hasCellComponent', file: 'src/utils/listCellRegistry.js', note: '运行时按 renderComponent 取组件' },
  { api: 'filterOptionsByEntity(options, entityCode, current)', file: 'src/shared/extension-entity-scope.js', note: '按实体收窄下拉，已选值始终保留' },
  { api: 'getCellComponentOptions()', file: 'src/utils/listCellRegistry.js', note: '全部单元格；设计器再按实体过滤' },
  { api: 'getCellValue(row, field)', file: 'src/shared/list-runtime/index.js', note: 'extData > data > row' },
  { api: 'formatListFieldValue(...)', file: 'src/shared/list-runtime/index.js', note: 'ListCellRenderer 用来算 value' },
  { api: 'entityListConfigApi.getExtensionOptions()', file: 'src/api/entityListConfig.js', note: 'GET /entity-list-config/extension-options' }
]

const backApis = [
  { api: 'ListFieldDataProvider', file: 'list/extension/ListFieldDataProvider.java', note: '列数据接口' },
  { api: 'ListFieldDataProviderRegistry', file: 'list/extension/ListFieldDataProviderRegistry.java', note: '收集 Bean、校验编码、给出下拉' },
  { api: 'TemplateListFieldDataProvider', file: 'list/extension/TemplateListFieldDataProvider.java', note: '内置 FIELD_TEMPLATE' },
  { api: 'ProjectCustomListFieldDataProvider', file: 'project/custom/ProjectCustomListFieldDataProvider.java', note: '项目模块示例，类型 PROJECT_CUSTOM_FIELD' },
  { api: 'ListFieldConditionEvaluator', file: 'list/extension/ListFieldConditionEvaluator.java', note: '补值之后的内存过滤' },
  { api: 'GET /api/entity-list-config/extension-options', file: 'EntityListConfigController', note: '设计器拉数据源选项' }
]

const schemaItemRows = [
  { key: 'key / label / type', note: '必填语义。type：text、textarea、number、boolean、select、json' },
  { key: 'required / defaultValue', note: '保存时校验必填；打开设计器时用 defaultValue 补空' },
  { key: 'min / max / step', note: '仅 number' },
  { key: 'options / multiple / placeholder / rows', note: 'select 的选项和多选；textarea/json 的行数和占位' },
  { key: 'description / helpKey', note: '项下方说明；json 项可挂帮助' },
  { key: 'group / advanced / order / priority', note: 'common 首屏，advanced 默认折叠；order 管组内顺序' },
  { key: 'visibleWhen', note: '按当前配置显隐，例如 { field: \'showLevel\', equals: true }' }
]

const jsonBagRows = [
  { bag: 'dataSourceConfig', who: '数据来源表单', shape: 'Provider 的 configSchema。如 { customerField, labelPrefix }' },
  { bag: 'renderConfig', who: '单元格参数表单', shape: '组件的 configSchema。如 { warningAt, dangerAt, showText, showLevel }' },
  { bag: 'queryConfig', who: '查询项', shape: '{ componentType, placeholder, defaultValue }。查询方式在 queryType' },
  { bag: 'columnConfig', who: '列展示与高级列布局', shape: '{ fixed: left|right|空, minWidth, showOverflowTooltip, quickCopy }。列宽/对齐在 width、align' }
]

const providerFullRows = [
  { method: 'getDataSourceType()', whenSet: '设计器保存这个编码；改名后旧列找不到数据源' },
  { method: 'getDisplayName() / getDescription()', whenSet: '下拉显示名和说明，出现在 extension-options' },
  { method: 'getSupportedEntityCodes()', whenSet: '只出现在这些实体的数据源下拉；运行时已保存列不受影响' },
  { method: 'supportsVirtualField() = false', whenSet: '虚拟列下拉里禁用；只能绑在实体字段上（一般不要这么做）' },
  { method: 'supportsQuery() = true', whenSet: '「查询」可勾；运行时先 enrich 再内存过滤' },
  { method: 'getConfigSchema()', whenSet: '数据来源表单；required 的 key 没填会拒保存' },
  { method: 'validateConfig(field, config)', whenSet: '必填过了之后再跑；用来拦业务上不合法的组合' },
  { method: 'enrich(...)', whenSet: '给当前页每条记录的 extData[fieldCode] 赋值' }
]

const providerMethods = [
  { method: 'getDataSourceType()', note: '必填。转大写后必须匹配 [A-Z][A-Z0-9_]{1,63}' },
  { method: 'getDisplayName() / getDescription()', note: '默认等于类型 / 空串。下拉用显示名' },
  { method: 'getSupportedEntityCodes()', note: '默认空=全部实体。只影响设计器下拉' },
  { method: 'supportsVirtualField()', note: '默认 true。false 时虚拟列不能选它' },
  { method: 'supportsQuery()', note: '默认 false。true 才能勾查询' },
  { method: 'getConfigSchema()', note: '默认空。项用 key/label/type/required/defaultValue' },
  { method: 'validateConfig(field, config)', note: '默认空。保存列时先校验必填再调它' },
  { method: 'enrich(records, fields, context)', note: '批量补数。fields 已按类型过滤。抛错则整次查询失败' }
]
</script>

<style scoped src="./dev-guide-shared.scss"></style>
<style scoped>
.guide-section h4 {
  margin: 16px 0 10px;
  font-size: 14px;
  color: #303133;
}
</style>
