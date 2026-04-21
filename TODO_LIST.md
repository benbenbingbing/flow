# 低代码平台功能优化 TODO 清单

> 对比参考：普元一开低代码平台
> 当前状态评估：已完成核心基础能力的 40% 左右

---

## 一、已完成的核心功能 ✅

### 1. 实体管理 (Entity Management)
- [x] 实体定义（表结构设计）
- [x] 字段类型支持（STRING/TEXT/INTEGER/DECIMAL/DATE/DATETIME/BOOLEAN/SELECT等）
- [x] 动态物理表创建（发布后自动生成数据库表）
- [x] 字段引用关系（REFERENCE/MULTI_REFERENCE）
- [x] 实体与流程绑定

### 2. 流程引擎 (Process Engine)
- [x] BPMN 流程设计器
- [x] 基础流程执行（开始节点、结束节点、用户任务）
- [x] 排他网关（Exclusive Gateway）
- [x] 并行网关（Parallel Gateway）
- [x] 多实例任务（会签/签章）
- [x] 流程变量传递
- [x] 流程状态流转

### 3. 表单引擎 (Form Engine)
- [x] 动态表单渲染（基于实体字段定义）
- [x] 基础表单组件（输入框、选择器、日期、开关等）
- [x] 实体选择器组件（支持单选/多选）
- [x] 表单与流程节点绑定

### 4. 系统管理 (System Management)
- [x] 用户管理
- [x] 角色管理
- [x] 组织机构管理
- [x] 用户组管理
- [x] 菜单管理（含实体关联配置）
- [x] 基础权限控制

### 5. 数据管理
- [x] 实体数据列表（通用页面）
- [x] 实体数据增删改查
- [x] 动态SQL查询

---

## 二、详细功能设计方案

---

### 【模块1】流程中心 (Process Center) 🔴高优先级

#### 1.1 功能描述
提供统一的流程任务管理中心，包括待办任务、已办任务、草稿箱、抄送/知会、我发起的流程等功能，支持流程审批的完整闭环。

#### 1.2 数据模型设计

```sql
-- 流程任务实例表（扩展Flowable的ACT_RU_TASK）
CREATE TABLE process_task_instance (
    id VARCHAR(64) PRIMARY KEY COMMENT '任务实例ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    task_id VARCHAR(64) COMMENT 'Flowable任务ID',
    task_key VARCHAR(100) COMMENT '任务节点Key',
    task_name VARCHAR(200) COMMENT '任务名称',
    process_definition_id VARCHAR(64) COMMENT '流程定义ID',
    process_name VARCHAR(200) COMMENT '流程名称',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    entity_data_id VARCHAR(64) COMMENT '关联实体数据ID',
    business_key VARCHAR(200) COMMENT '业务主键',
    assignee_id VARCHAR(64) COMMENT '被指派人ID',
    assignee_name VARCHAR(100) COMMENT '被指派人姓名',
    owner_id VARCHAR(64) COMMENT '任务所有人ID',
    candidate_users TEXT COMMENT '候选人ID列表（JSON）',
    candidate_groups TEXT COMMENT '候选组列表（JSON）',
    task_type ENUM('TODO','DONE','DRAFT','CC','DELEGATED') COMMENT '任务类型',
    action_type VARCHAR(50) COMMENT '操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN',
    action_comment TEXT COMMENT '处理意见',
    form_data JSON COMMENT '表单数据快照',
    due_time DATETIME COMMENT '截止时间',
    priority INT DEFAULT 50 COMMENT '优先级 0-100',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读',
    read_time DATETIME COMMENT '阅读时间',
    start_time DATETIME COMMENT '任务开始时间',
    end_time DATETIME COMMENT '任务结束时间',
    duration_ms BIGINT COMMENT '处理耗时（毫秒）',
    parent_task_id VARCHAR(64) COMMENT '父任务ID（用于会签）',
    root_task_id VARCHAR(64) COMMENT '根任务ID',
    delegation_state VARCHAR(20) COMMENT '委托状态：PENDING/RESOLVED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_process_instance (process_instance_id),
    INDEX idx_assignee_type (assignee_id, task_type),
    INDEX idx_entity (entity_code, entity_data_id),
    INDEX idx_business_key (business_key),
    INDEX idx_start_time (start_time),
    INDEX idx_due_time (due_time)
) COMMENT='流程任务实例表';

-- 常用意见表
CREATE TABLE process_common_opinion (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    opinion_content VARCHAR(500) NOT NULL COMMENT '意见内容',
    opinion_type VARCHAR(20) DEFAULT 'APPROVE' COMMENT '意见类型：APPROVE/REJECT',
    sort_order INT DEFAULT 0 COMMENT '排序',
    use_count INT DEFAULT 0 COMMENT '使用次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, sort_order)
) COMMENT='常用审批意见';

-- 流程抄送/知会记录表
CREATE TABLE process_cc_record (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    entity_code VARCHAR(100),
    entity_data_id VARCHAR(64),
    cc_user_id VARCHAR(64) COMMENT '抄送人ID',
    cc_user_name VARCHAR(100),
    sender_id VARCHAR(64) COMMENT '发送人ID',
    sender_name VARCHAR(100),
    cc_type ENUM('CC','NOTIFY') DEFAULT 'CC' COMMENT '抄送/知会',
    is_read TINYINT DEFAULT 0,
    read_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cc_user (cc_user_id, is_read),
    INDEX idx_process (process_instance_id)
) COMMENT='流程抄送记录';

-- 流程操作记录表（详细审计）
CREATE TABLE process_operation_log (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    operator_id VARCHAR(64) COMMENT '操作人ID',
    operator_name VARCHAR(100),
    operation_time DATETIME COMMENT '操作时间',
    operation_comment TEXT,
    old_value TEXT COMMENT '旧值（JSON）',
    new_value TEXT COMMENT '新值（JSON）',
    ip_address VARCHAR(50),
    user_agent TEXT,
    INDEX idx_process (process_instance_id, operation_time)
) COMMENT='流程操作日志';
```

#### 1.3 核心接口设计

```java
@RestController
@RequestMapping("/api/process-center")
public class ProcessCenterController {
    
    // ========== 待办任务 ==========
    @GetMapping("/todo/list")
    public PageResult<ProcessTaskVO> getTodoList(
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 查询当前登录用户的待办任务
    }
    
    // 批量审批
    @PostMapping("/todo/batch-approve")
    public Result<Void> batchApprove(@RequestBody BatchActionDTO dto) {
        // 批量同意
    }
    
    // ========== 已办任务 ==========
    @GetMapping("/done/list")
    public PageResult<ProcessTaskVO> getDoneList(
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 查询已办任务
    }
    
    // ========== 草稿箱 ==========
    @GetMapping("/draft/list")
    public PageResult<ProcessDraftVO> getDraftList(...) { }
    
    @PostMapping("/draft/save")
    public Result<String> saveDraft(@RequestBody ProcessDraftDTO dto) { }
    
    @PostMapping("/draft/submit/{draftId}")
    public Result<Void> submitDraft(@PathVariable String draftId) { }
    
    // ========== 抄送/知会 ==========
    @GetMapping("/cc/list")
    public PageResult<ProcessCcVO> getCcList(
            @RequestParam(required = false) Boolean isRead,
            ...) { }
    
    @PostMapping("/cc/mark-read/{ccId}")
    public Result<Void> markCcRead(@PathVariable String ccId) { }
    
    // ========== 我发起的 ==========
    @GetMapping("/my-started")
    public PageResult<MyProcessVO> getMyStartedProcess(...) { }
    
    // ========== 常用意见 ==========
    @GetMapping("/common-opinions")
    public List<CommonOpinionVO> getCommonOpinions(
            @RequestParam(required = false) String opinionType) { }
    
    @PostMapping("/common-opinions")
    public Result<Void> saveCommonOpinion(@RequestBody CommonOpinionDTO dto) { }
    
    // ========== 任务统计 ==========
    @GetMapping("/statistics")
    public ProcessStatisticsVO getStatistics() {
        // 返回待办数量、已办数量、草稿数量、抄送数量等
    }
}
```

#### 1.4 前端页面设计

```
流程中心模块 (/process-center)
│
├── 工作台首页 (ProcessWorkbench.vue)
│   ├── 待办统计卡片
│   ├── 快捷操作区
│   └── 最近处理记录
│
├── 待办任务 (TodoList.vue)
│   ├── 查询条件（流程类型、关键字、优先级、时间范围）
│   ├── 批量操作工具栏
│   ├── 待办列表
│   │   ├── 流程名称 + 业务数据摘要
│   │   ├── 发起人信息
│   │   ├── 到达时间 + 停留时长
│   │   ├── 优先级标识
│   │   └── 操作按钮（办理/转办/退回）
│   └── 审批弹窗
│       ├── 表单展示（只读/编辑）
│       ├── 审批意见输入
│       ├── 常用意见快捷选择
│       ├── 操作按钮（同意/驳回/转办/加签）
│       └── 流程图预览
│
├── 已办任务 (DoneList.vue)
│   └── 历史审批记录展示
│
├── 草稿箱 (DraftList.vue)
│   └── 草稿编辑与提交
│
├── 抄送/知会 (CcList.vue)
│   └── 抄送记录与标记已读
│
└── 我发起的 (MyProcess.vue)
    └── 我发起的流程实例跟踪
```

#### 1.5 实现要点

1. **任务同步机制**：通过 Flowable 的 TaskListener 将任务变更同步到 process_task_instance 表
2. **性能优化**：待办列表需要支持分页 + 索引优化，预计单表千万级数据
3. **移动端适配**：审批操作需要支持移动端，考虑使用响应式设计或单独移动端页面
4. **消息通知**：集成 WebSocket 或 SSE 实现待办任务实时推送
5. **批量审批**：支持选中多个任务批量同意（需校验表单是否必填）

---

### 【模块2】视图引擎 (View Engine) 🔴高优先级

#### 2.1 功能描述
提供可视化的数据视图设计器，支持列表视图、图表视图、看板视图、详情视图的配置化开发，无需编写代码即可定义数据展示界面。

#### 2.2 数据模型设计

```sql
-- 视图定义表
CREATE TABLE view_definition (
    id VARCHAR(64) PRIMARY KEY,
    view_code VARCHAR(100) UNIQUE NOT NULL COMMENT '视图编码',
    view_name VARCHAR(200) NOT NULL COMMENT '视图名称',
    view_type ENUM('LIST','CHART','DASHBOARD','DETAIL','TREE') NOT NULL COMMENT '视图类型',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    data_source_type ENUM('ENTITY','SQL','API','MIX') DEFAULT 'ENTITY' COMMENT '数据源类型',
    data_source_config JSON COMMENT '数据源配置（SQL/API等）',
    layout_config JSON NOT NULL COMMENT '布局配置（JSON）',
    query_config JSON COMMENT '查询条件配置',
    toolbar_config JSON COMMENT '工具栏按钮配置',
    operation_config JSON COMMENT '行操作按钮配置',
    style_config JSON COMMENT '样式配置',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认视图',
    version INT DEFAULT 1 COMMENT '版本号',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_entity (entity_code, view_type),
    INDEX idx_code (view_code)
) COMMENT='视图定义表';

-- 视图字段配置表
CREATE TABLE view_field_config (
    id VARCHAR(64) PRIMARY KEY,
    view_id VARCHAR(64) NOT NULL,
    field_code VARCHAR(100) NOT NULL COMMENT '字段编码',
    field_name VARCHAR(200) COMMENT '字段显示名',
    field_type VARCHAR(50) COMMENT '字段类型',
    sort_order INT DEFAULT 0 COMMENT '显示顺序',
    width VARCHAR(20) COMMENT '列宽（如：150px/15%）',
    align VARCHAR(10) DEFAULT 'left' COMMENT '对齐方式',
    is_show TINYINT DEFAULT 1 COMMENT '是否显示',
    is_sortable TINYINT DEFAULT 0 COMMENT '是否可排序',
    is_searchable TINYINT DEFAULT 0 COMMENT '是否可搜索',
    formatter_type VARCHAR(50) COMMENT '格式化类型：TEXT/DATE/DATETIME/CODE/DICT/LINK/TAG/IMAGE',
    formatter_config JSON COMMENT '格式化配置',
    fixed VARCHAR(10) COMMENT '固定列：left/right',
    show_in_list TINYINT DEFAULT 1,
    show_in_detail TINYINT DEFAULT 1,
    show_in_export TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_view (view_id, sort_order)
) COMMENT='视图字段配置表';

-- 视图查询条件配置表
CREATE TABLE view_query_config (
    id VARCHAR(64) PRIMARY KEY,
    view_id VARCHAR(64) NOT NULL,
    field_code VARCHAR(100) NOT NULL,
    field_name VARCHAR(200),
    query_type VARCHAR(50) COMMENT '查询类型：EQ/NE/LIKE/LEFT_LIKE/RIGHT_LIKE/GT/LT/BETWEEN/IN/NOT_IN/NULL/NOT_NULL',
    component_type VARCHAR(50) COMMENT '组件类型：INPUT/SELECT/DATE/DATE_RANGE/NUMBER/NUMBER_RANGE/CASCADE/ENTITY_SELECT',
    component_config JSON COMMENT '组件配置（选项、日期格式等）',
    default_value TEXT COMMENT '默认值',
    placeholder VARCHAR(200),
    sort_order INT DEFAULT 0,
    is_required TINYINT DEFAULT 0,
    is_advanced TINYINT DEFAULT 0 COMMENT '是否高级查询',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_view (view_id, sort_order)
) COMMENT='视图查询条件配置表';

-- 视图按钮配置表
CREATE TABLE view_button_config (
    id VARCHAR(64) PRIMARY KEY,
    view_id VARCHAR(64) NOT NULL,
    button_code VARCHAR(100) NOT NULL,
    button_name VARCHAR(200),
    button_type ENUM('TOOLBAR','ROW','BATCH') NOT NULL COMMENT '按钮位置',
    action_type VARCHAR(50) COMMENT '动作类型：ADD/EDIT/DELETE/VIEW/EXPORT/IMPORT/CUSTOM/SERVICE/JUMP',
    action_config JSON COMMENT '动作配置',
    icon VARCHAR(100),
    style VARCHAR(50) COMMENT '样式：primary/success/warning/danger',
    sort_order INT DEFAULT 0,
    visible_condition TEXT COMMENT '显示条件（SpEL表达式）',
    disabled_condition TEXT COMMENT '禁用条件',
    permission_code VARCHAR(200) COMMENT '权限标识',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_view (view_id, button_type, sort_order)
) COMMENT='视图按钮配置表';
```

#### 2.3 视图类型详细设计

##### 2.3.1 列表视图 (List View)

**布局配置示例 (layout_config):**
```json
{
  "type": "LIST",
  "tableConfig": {
    "stripe": true,
    "border": true,
    "size": "medium",
    "showIndex": true,
    "showSelection": true,
    "height": "auto",
    "pagination": {
      "enabled": true,
      "pageSize": 10,
      "pageSizes": [10, 20, 50, 100],
      "layout": "total, sizes, prev, pager, next, jumper"
    }
  },
  "columnConfig": {
    "showOverflowTooltip": true,
    "resizable": true
  }
}
```

**支持的列格式化类型:**
| 类型 | 说明 | 配置示例 |
|-----|------|---------|
| TEXT | 普通文本 | `{"maxLength": 20}` |
| DATE | 日期 | `{"format": "yyyy-MM-dd"}` |
| DATETIME | 日期时间 | `{"format": "yyyy-MM-dd HH:mm"}` |
| DICT | 数据字典 | `{"dictType": "sys_status"}` |
| TAG | 标签展示 | `{"colorMap": {"0": "success", "1": "danger"}}` |
| LINK | 链接 | `{"url": "/detail/{id}", "target": "_blank"}` |
| IMAGE | 图片 | `{"width": 50, "height": 50, "preview": true}` |
| PROGRESS | 进度条 | `{"color": "#409EFF"}` |
| CUSTOM | 自定义 | `{"render": "${value} - ${row.status}"}` |

##### 2.3.2 图表视图 (Chart View)

**支持的图表类型:**
- 柱状图 (bar)
- 折线图 (line)
- 饼图 (pie)
- 环形图 (donut)
- 雷达图 (radar)
- 散点图 (scatter)
- 漏斗图 (funnel)
- 仪表盘 (gauge)

**配置示例:**
```json
{
  "type": "CHART",
  "chartType": "bar",
  "dataConfig": {
    "xAxisField": "dept_name",
    "yAxisFields": [{"field": "amount", "agg": "sum", "name": "金额"}],
    "seriesField": "year"
  },
  "chartConfig": {
    "title": "部门销售金额统计",
    "legend": {"show": true, "position": "top"},
    "toolbox": ["dataView", "magicType", "restore", "saveAsImage"]
  }
}
```

##### 2.3.3 看板视图 (Dashboard View)

**组件库:**
- 统计卡片 (StatisticCard)
- 趋势图表 (TrendChart)
- 排行榜 (RankingList)
- 待办列表 (TodoList)
- 快捷入口 (QuickEntry)
- 系统公告 (Announcement)
- 日历组件 (Calendar)
- 数据表格 (DataTable)

**布局系统:**
- 基于 24 栅格系统
- 支持拖拽调整位置
- 支持调整组件大小

#### 2.4 前端组件架构

```
视图引擎模块 (/views/engine)
│
├── 视图设计器 (ViewDesigner.vue)
│   ├── 左侧：组件/字段库
│   ├── 中间：画布区域
│   │   ├── 拖拽画布
│   │   ├── 属性面板
│   │   └── 实时预览
│   └── 右侧：属性配置面板
│
├── 视图渲染器 (ViewRenderer.vue)
│   ├── 动态组件加载
│   ├── 数据获取与绑定
│   └── 事件处理
│
├── 列表视图组件 (ListView.vue)
│   ├── 查询区域 (ViewQueryArea.vue)
│   ├── 工具栏 (ViewToolbar.vue)
│   ├── 数据表格 (ViewDataTable.vue)
│   └── 分页组件
│
├── 图表视图组件 (ChartView.vue)
│   └── 基于 ECharts 封装
│
├── 看板视图组件 (DashboardView.vue)
│   └── 网格布局 + 组件库
│
└── 详情视图组件 (DetailView.vue)
    └── 基于实体表单配置渲染
```

#### 2.5 实现要点

1. **数据权限集成**: 视图查询需要自动注入数据权限过滤条件
2. **性能优化**: 
   - 大数据量列表支持虚拟滚动
   - 图表数据支持后端聚合计算
3. **扩展机制**: 支持自定义组件注册
4. **移动端适配**: 列表视图自动切换为卡片式布局

---

### 【模块3】报表引擎 (Report Engine) 🔴高优先级

#### 3.1 功能描述
提供企业级报表设计能力，支持类Excel报表设计、交叉报表、图表报表、大屏设计，满足复杂的数据统计和分析需求。

#### 3.2 数据模型设计

```sql
-- 报表定义表
CREATE TABLE report_definition (
    id VARCHAR(64) PRIMARY KEY,
    report_code VARCHAR(100) UNIQUE NOT NULL,
    report_name VARCHAR(200) NOT NULL,
    report_type ENUM('TABLE','CROSS','CHART','DASHBOARD','PRINT') NOT NULL,
    category_id VARCHAR(64) COMMENT '报表分类ID',
    dataset_config JSON COMMENT '数据集配置（支持多数据集）',
    layout_config JSON NOT NULL COMMENT '报表布局配置',
    params_config JSON COMMENT '报表参数配置',
    style_config JSON COMMENT '样式配置',
    permission_config JSON COMMENT '权限配置',
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_code (report_code)
) COMMENT='报表定义表';

-- 报表数据集定义
CREATE TABLE report_dataset (
    id VARCHAR(64) PRIMARY KEY,
    report_id VARCHAR(64) NOT NULL,
    dataset_code VARCHAR(100) NOT NULL,
    dataset_name VARCHAR(200),
    dataset_type ENUM('SQL','ENTITY','API','SCRIPT') NOT NULL,
    source_config JSON NOT NULL COMMENT '数据源配置',
    field_mappings JSON COMMENT '字段映射',
    cache_config JSON COMMENT '缓存配置',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id)
) COMMENT='报表数据集';

-- 报表分类表
CREATE TABLE report_category (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64) DEFAULT '0',
    category_code VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
) COMMENT='报表分类';

-- 报表订阅/定时任务
CREATE TABLE report_schedule (
    id VARCHAR(64) PRIMARY KEY,
    report_id VARCHAR(64) NOT NULL,
    schedule_name VARCHAR(200),
    cron_expression VARCHAR(100) NOT NULL,
    param_values JSON COMMENT '参数值',
    export_format ENUM('PDF','EXCEL','HTML') DEFAULT 'EXCEL',
    notify_type ENUM('EMAIL','MSG','NONE') DEFAULT 'EMAIL',
    notify_targets TEXT COMMENT '通知目标',
    last_run_time DATETIME,
    next_run_time DATETIME,
    status VARCHAR(20) DEFAULT 'ENABLED',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_next_run (next_run_time, status)
) COMMENT='报表定时任务';
```

#### 3.3 报表类型详细设计

##### 3.3.1 普通表格报表

**功能特性:**
- 类Excel单元格设计
- 合并单元格
- 单元格公式计算
- 条件格式（高亮显示）
- 数据分组与汇总
- 多级表头

**布局配置示例:**
```json
{
  "type": "TABLE",
  "cells": [
    {
      "row": 0,
      "col": 0,
      "rowspan": 1,
      "colspan": 3,
      "type": "TITLE",
      "value": "销售统计报表",
      "style": {"fontSize": 16, "bold": true, "align": "center"}
    },
    {
      "row": 1,
      "col": 0,
      "type": "HEADER",
      "value": "部门",
      "style": {"backgroundColor": "#f5f5f5", "bold": true}
    },
    {
      "row": 2,
      "col": 0,
      "type": "DATA",
      "field": "deptName",
      "dataset": "ds1"
    },
    {
      "row": 2,
      "col": 1,
      "type": "DATA",
      "field": "amount",
      "dataset": "ds1",
      "formatter": "number:2",
      "aggregate": "sum"
    },
    {
      "row": 2,
      "col": 2,
      "type": "FORMULA",
      "formula": "=B{row}/B{totalRow}",
      "formatter": "percent:2"
    }
  ]
}
```

##### 3.3.2 交叉报表 (Cross Report)

**功能特性:**
- 行列交叉分析
- 多级行头/列头
- 数据透视功能
- 同比/环比计算
- 占比计算
- 小计/合计

**配置示例:**
```json
{
  "type": "CROSS",
  "dataset": "ds1",
  "rowFields": [
    {"field": "region", "name": "地区"},
    {"field": "province", "name": "省份"}
  ],
  "columnFields": [
    {"field": "year", "name": "年份"},
    {"field": "quarter", "name": "季度"}
  ],
  "dataFields": [
    {
      "field": "amount",
      "name": "销售额",
      "aggregate": "sum",
      "format": "#,##0.00"
    },
    {
      "field": "amount",
      "name": "同比增长",
      "calculate": "yoy",
      "format": "percent"
    }
  ],
  "showSubtotal": true,
  "showGrandTotal": true
}
```

##### 3.3.3 大屏设计器

**组件库:**
- 基础组件：文本、图片、矩形、圆形
- 图表组件：柱状图、折线图、饼图、雷达图、地图
- 数据组件：数字翻牌器、进度条、仪表盘、排行榜
- 布局组件：卡片、标签页、轮播

**特性:**
- 绝对定位布局
- 组件拖拽
- 图层管理
- 数据绑定
- 自动刷新

#### 3.4 前端架构

```
报表引擎模块 (/views/report)
│
├── 报表设计器 (ReportDesigner.vue)
│   ├── Excel风格设计器 (TableReportDesigner.vue)
│   ├── 交叉报表设计器 (CrossReportDesigner.vue)
│   ├── 图表配置器 (ChartConfigPanel.vue)
│   └── 大屏设计器 (DashboardDesigner.vue)
│       ├── 组件库面板
│       ├── 画布区域（绝对定位）
│       ├── 图层管理器
│       └── 属性面板
│
├── 数据集配置 (DatasetConfig.vue)
│   ├── SQL编辑器（带语法高亮）
│   ├── 字段映射配置
│   └── 预览与测试
│
├── 报表预览 (ReportPreview.vue)
│   ├── 参数输入面板
│   ├── 报表渲染
│   └── 导出功能（PDF/Excel/HTML）
│
└── 报表查看器 (ReportViewer.vue)
    ├── 在线查看
    ├── 分页浏览
    └── 打印支持
```

#### 3.5 实现要点

1. **打印支持**: 集成浏览器打印或使用 pdfmake 生成PDF
2. **大数据量**: 报表数据超过10万条时采用异步生成+下载链接方式
3. **数据安全**: SQL数据集需要参数化查询防注入
4. **缓存机制**: 报表结果支持缓存，减少数据库压力

---

### 【模块4】服务编排 (Service Orchestration) 🔴高优先级

#### 4.1 功能描述
提供可视化的服务编排设计器，支持通过拖拽方式组合原子服务，实现复杂业务逻辑的编排，支持条件判断、循环、异常处理等控制流。

#### 4.2 数据模型设计

```sql
-- 服务定义表
CREATE TABLE service_definition (
    id VARCHAR(64) PRIMARY KEY,
    service_code VARCHAR(100) UNIQUE NOT NULL,
    service_name VARCHAR(200) NOT NULL,
    service_type ENUM('ORCHESTRATION','SCRIPT','PROXY') NOT NULL,
    category_id VARCHAR(64),
    description TEXT,
    input_params JSON COMMENT '输入参数定义',
    output_params JSON COMMENT '输出参数定义',
    flow_config JSON NOT NULL COMMENT '流程配置（DAG）',
    variables JSON COMMENT '变量定义',
    timeout_ms INT DEFAULT 30000 COMMENT '超时时间',
    retry_config JSON COMMENT '重试配置',
    exception_handler JSON COMMENT '异常处理配置',
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_code (service_code)
) COMMENT='服务定义表';

-- 服务节点定义表
CREATE TABLE service_node (
    id VARCHAR(64) PRIMARY KEY,
    service_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(100) NOT NULL COMMENT '节点唯一标识',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
    node_name VARCHAR(200),
    position_x DECIMAL(10,2) COMMENT '画布X坐标',
    position_y DECIMAL(10,2) COMMENT '画布Y坐标',
    config JSON NOT NULL COMMENT '节点配置',
    input_mapping JSON COMMENT '输入参数映射',
    output_mapping JSON COMMENT '输出参数映射',
    next_nodes JSON COMMENT '下游节点ID列表',
    condition_expression TEXT COMMENT '条件表达式（用于条件分支）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service (service_id),
    UNIQUE KEY uk_service_node (service_id, node_id)
) COMMENT='服务节点表';

-- 服务执行记录表
CREATE TABLE service_execution_log (
    id VARCHAR(64) PRIMARY KEY,
    service_id VARCHAR(64) NOT NULL,
    execution_id VARCHAR(100) NOT NULL COMMENT '执行实例ID',
    trigger_type ENUM('MANUAL','SCHEDULE','EVENT','API') NOT NULL,
    trigger_source VARCHAR(200) COMMENT '触发来源',
    input_params TEXT COMMENT '输入参数',
    output_result TEXT COMMENT '输出结果',
    status ENUM('RUNNING','SUCCESS','FAILED','TIMEOUT') NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    duration_ms INT,
    node_executions JSON COMMENT '节点执行详情',
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service (service_id, start_time),
    INDEX idx_execution (execution_id),
    INDEX idx_status (status)
) COMMENT='服务执行记录表';
```

#### 4.3 节点类型设计

| 节点类型 | 类型编码 | 功能说明 |
|---------|---------|---------|
| 开始节点 | START | 流程起始点 |
| 结束节点 | END | 流程结束点 |
| 实体操作 | ENTITY_CRUD | 实体增删改查操作 |
| HTTP调用 | HTTP | 调用外部HTTP服务 |
| 数据库操作 | SQL | 执行SQL语句 |
| 脚本执行 | SCRIPT | 执行Groovy/JavaScript脚本 |
| 条件分支 | CONDITION | 条件判断分支 |
| 并行分支 | PARALLEL | 并行执行多个分支 |
| 聚合节点 | JOIN | 等待并行分支完成 |
| 循环节点 | LOOP | 循环执行 |
| 子流程调用 | SUBFLOW | 调用其他服务编排 |
| 流程操作 | PROCESS | 启动/操作流程实例 |
| 消息发送 | MESSAGE | 发送消息通知 |
| 延时等待 | DELAY | 延时等待 |
| 数据映射 | MAPPING | 数据转换映射 |
| 异常捕获 | TRY_CATCH | 异常处理 |
| 日志记录 | LOG | 记录日志 |

#### 4.4 节点配置示例

**实体操作节点:**
```json
{
  "nodeType": "ENTITY_CRUD",
  "config": {
    "operation": "QUERY", // CREATE/UPDATE/DELETE/QUERY/COUNT/EXIST
    "entityCode": "order",
    "queryCondition": {
      "field": "status",
      "operator": "EQ",
      "value": "${input.status}"
    },
    "updateData": {
      "status": "APPROVED",
      "approveTime": "${sys.currentTime}"
    },
    "resultMapping": {
      "records": "orderList",
      "total": "totalCount"
    }
  }
}
```

**HTTP调用节点:**
```json
{
  "nodeType": "HTTP",
  "config": {
    "method": "POST",
    "url": "https://api.example.com/order",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "Bearer ${token}"
    },
    "body": "${input.orderData}",
    "timeout": 10000,
    "retry": {
      "maxAttempts": 3,
      "retryInterval": 1000
    }
  }
}
```

**脚本执行节点:**
```json
{
  "nodeType": "SCRIPT",
  "config": {
    "scriptType": "GROOVY",
    "script": """
      def amount = input.orderAmount
      def discount = amount > 1000 ? 0.9 : 0.95
      return [
        finalAmount: amount * discount,
        discountRate: discount
      ]
    """
  }
}
```

**条件分支节点:**
```json
{
  "nodeType": "CONDITION",
  "config": {
    "conditions": [
      {
        "name": "金额大于1000",
        "expression": "${input.amount} > 1000",
        "nextNode": "node_high"
      },
      {
        "name": "默认分支",
        "expression": "true",
        "nextNode": "node_normal"
      }
    ]
  }
}
```

#### 4.5 执行引擎设计

```java
@Service
public class ServiceOrchestrationEngine {
    
    /**
     * 执行服务编排
     */
    public ExecutionResult execute(String serviceId, Map<String, Object> inputParams) {
        // 1. 加载服务定义
        ServiceDefinition service = serviceRepository.findById(serviceId);
        
        // 2. 创建执行上下文
        ExecutionContext context = new ExecutionContext();
        context.setInputParams(inputParams);
        context.setVariables(new HashMap<>());
        
        // 3. 解析DAG为执行计划
        ExecutionPlan plan = parseToExecutionPlan(service.getFlowConfig());
        
        // 4. 按拓扑排序执行节点
        for (NodeExecutionUnit unit : plan.getSortedNodes()) {
            NodeExecutor executor = nodeExecutorFactory.getExecutor(unit.getNodeType());
            NodeResult result = executor.execute(unit, context);
            
            if (!result.isSuccess()) {
                // 处理异常
                handleException(service, unit, result, context);
                break;
            }
            
            // 更新上下文
            context.setNodeOutput(unit.getNodeId(), result.getOutput());
            
            // 确定下一个执行节点
            String nextNodeId = determineNextNode(unit, result, context);
            if (nextNodeId == null) break;
        }
        
        // 5. 返回执行结果
        return buildResult(context);
    }
}
```

#### 4.6 前端设计器架构

```
服务编排设计器 (/views/service)
│
├── 画布区域 (ServiceCanvas.vue)
│   ├── 基于 X6 或 ReactFlow 的 DAG 画布
│   ├── 节点拖拽
│   ├── 连线编辑
│   ├── 缩放/平移
│   └── 网格对齐
│
├── 节点库面板 (NodeLibrary.vue)
│   ├── 按类型分组展示节点
│   └── 拖拽到画布创建节点
│
├── 属性配置面板 (NodePropertyPanel.vue)
│   ├── 根据节点类型动态渲染配置表单
│   ├── 参数映射配置
│   └── 测试数据配置
│
├── 调试执行面板 (ServiceDebugger.vue)
│   ├── 单步执行
│   ├── 断点设置
│   ├── 变量查看
│   └── 执行日志
│
└── 版本管理 (ServiceVersion.vue)
    ├── 版本列表
    ├── 版本对比
    └── 版本回滚
```

#### 4.7 实现要点

1. **DAG执行顺序**: 使用拓扑排序确保节点按依赖顺序执行
2. **并行执行**: 并行分支使用线程池异步执行，聚合节点等待所有分支完成
3. **事务管理**: 支持声明式事务，可在节点级别配置事务边界
4. **断点调试**: 支持在执行过程中设置断点，暂停查看变量状态
5. **性能监控**: 记录每个节点的执行耗时，识别性能瓶颈

---

### 【模块5】脚本规则引擎 (Script Rule Engine) 🟡中优先级

#### 5.1 功能描述
提供前后端脚本执行能力，支持在表单事件、流程规则、服务编排中编写自定义逻辑，实现低代码无法满足的复杂业务需求。

#### 5.2 技术方案

**后端脚本引擎 (Groovy):**
```java
@Service
public class GroovyScriptEngine {
    
    private final GroovyScriptEngineImpl engine;
    private final ConcurrentHashMap<String, Script> scriptCache;
    
    public Object execute(String scriptId, String scriptContent, Map<String, Object> context) {
        try {
            // 从缓存获取或编译脚本
            Script script = scriptCache.computeIfAbsent(scriptId, 
                k -> engine.createScript(scriptContent, new Binding()));
            
            // 设置上下文变量
            Binding binding = new Binding(context);
            script.setBinding(binding);
            
            // 在沙箱中执行
            return sandbox.execute(() -> script.run());
        } catch (Exception e) {
            throw new ScriptExecutionException("脚本执行失败: " + e.getMessage(), e);
        }
    }
}
```

**安全沙箱配置:**
```java
@Configuration
public class ScriptSandboxConfig {
    
    @Bean
    public GroovySandbox sandbox() {
        return new GroovySandbox()
            // 禁止的类
            .denyClass(System.class, Runtime.class, ProcessBuilder.class)
            // 禁止的包
            .denyPackage("java.io", "java.net", "java.nio")
            // 允许的白名单
            .allowClass(String.class, Integer.class, BigDecimal.class, 
                       Date.class, LocalDateTime.class, Math.class)
            // 超时控制
            .timeout(5000); // 5秒超时
    }
}
```

**前端脚本引擎 (JavaScript):**
```typescript
// 在沙箱iframe中执行用户脚本
class FrontendScriptEngine {
    private sandbox: HTMLIFrameElement;
    
    execute(script: string, context: Record<string, any>): any {
        const sandboxWindow = this.sandbox.contentWindow;
        
        // 注入安全的上下文
        sandboxWindow.__CONTEXT__ = {
            ...context,
            // 安全的工具函数
            utils: {
                formatDate: (date, format) => dayjs(date).format(format),
                calculate: (expr) => safeCalculate(expr),
                request: (url, options) => safeRequest(url, options)
            }
        };
        
        // 执行脚本
        return sandboxWindow.eval(`
            (function() {
                const { formData, entityApi, utils } = __CONTEXT__;
                ${script}
            })()
        `);
    }
}
```

#### 5.3 应用场景

| 应用场景 | 执行时机 | 可用变量 | 返回值 |
|---------|---------|---------|--------|
| 表单字段默认值 | 表单加载 | `user`, `sys` | 字段值 |
| 表单字段计算 | 依赖字段变更 | `formData`, `changedField` | 计算结果 |
| 表单校验规则 | 提交前 | `formData` | boolean/错误信息 |
| 字段显隐控制 | 表单加载/字段变更 | `formData` | boolean |
| 流程参与者规则 | 任务创建 | `processVars`, `entityData` | 用户ID列表 |
| 流程条件判断 | 网关流转 | `processVars` | boolean |
| 服务编排脚本 | 节点执行 | `input`, `variables` | 任意对象 |

#### 5.4 脚本编辑器组件

```vue
<template>
  <div class="script-editor">
    <!-- Monaco Editor 代码编辑器 -->
    <MonacoEditor
      v-model="scriptContent"
      language="groovy"
      theme="vs-dark"
      :options="editorOptions"
    />
    
    <!-- 变量提示面板 -->
    <VariablePanel :available-vars="availableVariables" />
    
    <!-- 测试执行区域 -->
    <TestPanel 
      @execute="handleTestExecute"
      :result="testResult"
      :logs="executionLogs"
    />
  </div>
</template>
```

---

### 【模块6】子表/明细表 (Sub-table) 🔴高优先级

#### 6.1 功能描述
支持在表单中嵌入子表（一对多关系），实现主子表数据的同时录入和编辑，如订单与订单明细、报销单与费用明细等场景。

#### 6.2 数据模型扩展

```sql
-- 实体字段表增加子表相关配置
ALTER TABLE entity_field ADD COLUMN sub_table_config JSON COMMENT '子表配置';

/* 子表配置示例:
{
  "refEntityCode": "order_item",      // 子表实体编码
  "foreignKeyField": "order_id",      // 子表外键字段
  "displayMode": "INLINE",            // INLINE(行内)/DIALOG(弹窗)/TAB(标签页)
  "maxRows": 100,                     // 最大行数
  "minRows": 1,                       // 最小行数
  "allowAdd": true,                   // 允许新增
  "allowDelete": true,                // 允许删除
  "allowImport": true,                // 允许导入
  "summaryFields": [                  // 汇总字段
    {"field": "amount", "agg": "sum", "targetField": "totalAmount"}
  ]
}
*/
```

#### 6.3 前端组件设计

```vue
<template>
  <div class="sub-table-field">
    <!-- 工具栏 -->
    <div class="sub-table-toolbar">
      <el-button type="primary" @click="handleAddRow" v-if="config.allowAdd">
        <el-icon><Plus /></el-icon> 添加行
      </el-button>
      <el-button @click="handleImport" v-if="config.allowImport">
        <el-icon><Upload /></el-icon> 导入
      </el-button>
    </div>
    
    <!-- 子表数据表格 -->
    <el-table :data="tableData" border>
      <el-table-column type="index" width="50" />
      
      <!-- 动态列 -->
      <el-table-column 
        v-for="field in subEntityFields" 
        :key="field.fieldCode"
        :prop="field.fieldCode"
        :label="field.fieldName"
        :width="field.columnWidth"
      >
        <template #default="{ row, $index }">
          <!-- 根据字段类型渲染编辑组件 -->
          <FormFieldRenderer
            v-model="row[field.fieldCode]"
            :field="field"
            :row-index="$index"
            @change="handleFieldChange($index, field.fieldCode, $event)"
          />
        </template>
      </el-table-column>
      
      <!-- 操作列 -->
      <el-table-column label="操作" width="100" v-if="config.allowDelete">
        <template #default="{ $index }">
          <el-button link type="danger" @click="handleDeleteRow($index)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 汇总行 -->
    <div class="summary-row" v-if="config.summaryFields?.length">
      <span v-for="item in summaryConfig" :key="item.field">
        {{ item.label }}: <strong>{{ item.value }}</strong>
      </span>
    </div>
  </div>
</template>

<script setup>
// 主子表数据保存时结构
const saveData = {
  entityCode: 'order',
  data: {
    orderNo: 'ORD-001',
    totalAmount: 10000,
    // ... 主表字段
  },
  subTables: {
    order_item: [  // 子表数据
      { itemNo: 'ITEM-001', productName: '商品A', quantity: 2, price: 100 },
      { itemNo: 'ITEM-002', productName: '商品B', quantity: 1, price: 200 }
    ]
  }
}
</script>
```

#### 6.4 后端保存逻辑

```java
@Service
public class EntityDataService {
    
    @Transactional
    public EntityDataDTO saveWithSubTables(EntityDataDTO dto) {
        // 1. 保存主表数据
        String mainDataId = saveMainTable(dto);
        
        // 2. 保存子表数据
        if (dto.getSubTables() != null) {
            dto.getSubTables().forEach((subEntityCode, subDataList) -> {
                // 删除原有子表数据
                deleteSubTableData(subEntityCode, mainDataId);
                
                // 插入新的子表数据
                for (Map<String, Object> subData : subDataList) {
                    subData.put("parentId", mainDataId);
                    saveSubTable(subEntityCode, subData);
                }
            });
        }
        
        return dto;
    }
}
```

---

### 【模块7】表单多态 (Form Polymorphism) 🟡中优先级

#### 7.1 功能描述
支持同一表单在不同状态下展示不同的字段和布局，如新增态显示所有字段，查看态只读，审批态只显示审批相关字段等。

#### 7.2 实现方案

```sql
-- 实体表单表增加多态配置
ALTER TABLE entity_form ADD COLUMN polymorphism_config JSON COMMENT '多态配置';

/* 多态配置示例:
{
  "states": [
    {
      "stateCode": "CREATE",
      "stateName": "新增态",
      "visibleFields": ["field1", "field2", "field3"],
      "editableFields": ["field1", "field2", "field3"],
      "requiredFields": ["field1", "field2"],
      "layout": "layout_create"
    },
    {
      "stateCode": "EDIT",
      "stateName": "编辑态",
      "visibleFields": ["field1", "field2", "field3", "field4"],
      "editableFields": ["field2", "field3"],
      "requiredFields": ["field2"],
      "readonlyFields": ["field1", "field4"],
      "layout": "layout_edit"
    },
    {
      "stateCode": "VIEW",
      "stateName": "查看态",
      "visibleFields": ["field1", "field2", "field4"],
      "editableFields": [],
      "layout": "layout_view"
    },
    {
      "stateCode": "APPROVE",
      "stateName": "审批态",
      "visibleFields": ["field1", "field2", "approveComment"],
      "editableFields": ["approveComment"],
      "requiredFields": ["approveComment"]
    }
  ]
}
*/
```

---

### 【模块8】应用中心 (Application Center) 🟡中优先级

#### 8.1 功能描述
管理低代码应用内的各类资源，支持对菜单、接口、数据资源进行统一授权管理，提供系统级配置能力。

#### 8.2 功能模块

```
应用中心
│
├── 资源授权管理
│   ├── 菜单授权（角色-菜单权限）
   ├── 接口授权（API权限控制）
   └── 数据授权（行级/字段级权限）
│
├── 系统设置
│   ├── 登录安全策略（失败锁定、IP限制）
   ├── 密码策略（复杂度、过期时间）
   ├── 会话超时设置
   └── 系统公告管理
│
├── 业务字典
│   ├── 字典类型管理
│   └── 字典数据管理
│
├── 系统变量
│   ├── 全局变量配置
│   └── 环境变量管理
│
└── 定时任务
    ├── 任务配置
    ├── 执行日志
    └── 调度监控
```

---

### 【模块9】工作台 (Workbench) 🟡中优先级

#### 9.1 功能描述
提供可配置的个人工作台门户，支持多种门户布局，提供常用Widget组件，实现千人千面的首页体验。

#### 9.2 Widget组件库

| 组件名称 | 功能说明 | 配置项 |
|---------|---------|--------|
| 待办任务 | 显示当前用户的待办列表 | 显示数量、流程类型筛选 |
| 快捷入口 | 常用功能快速访问 | 图标、链接、权限 |
| 数据看板 | 关键指标统计卡片 | 指标配置、刷新频率 |
| 系统公告 | 最新公告列表 | 显示条数、滚动速度 |
| 日程日历 | 工作日历展示 | 日程来源、提醒方式 |
| 最近使用 | 最近访问的记录 | 显示数量 |
| 我的流程 | 我发起的流程跟踪 | 状态筛选 |
| 图表组件 | 自定义图表展示 | 图表配置 |

---

### 【模块10】多租户支持 (Multi-tenancy) 🟢低优先级

#### 10.1 实现方案对比

| 方案 | 实现复杂度 | 数据隔离性 | 适用场景 |
|-----|-----------|-----------|---------|
| 独立数据库 | 低 | 最高 | 大型企业客户 |
| 共享数据库独立Schema | 中 | 高 | 中型企业客户 |
| 共享数据库共享Schema+租户ID | 高 | 中 | SaaS模式、小型客户 |

#### 10.2 推荐方案：共享Schema + 租户ID

```sql
-- 所有业务表增加租户ID字段
ALTER TABLE entity_definition ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE process_definition ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT '0';
-- ... 其他表

-- 创建租户信息表
CREATE TABLE sys_tenant (
    id VARCHAR(64) PRIMARY KEY,
    tenant_code VARCHAR(100) UNIQUE NOT NULL,
    tenant_name VARCHAR(200) NOT NULL,
    tenant_type ENUM('PLATFORM','ENTERPRISE','PERSONAL') NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    expire_time DATETIME,
    resource_limit JSON COMMENT '资源限制（用户数、存储空间等）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='租户信息表';
```

---

## 三、功能对比矩阵

| 功能模块 | 当前状态 | 一开平台 | 差距评估 |
|---------|---------|---------|---------|
| **实体管理** | 70% | 100% | 缺少查询实体、服务实体概念 |
| **表单引擎** | 60% | 100% | 缺少子表、多态、移动端适配 |
| **流程引擎** | 70% | 100% | 缺少自由流、事务分割、高级事件 |
| **视图引擎** | 0% | 100% | ❌ 完全缺失 |
| **服务编排** | 0% | 100% | ❌ 完全缺失 |
| **报表引擎** | 0% | 100% | ❌ 完全缺失 |
| **脚本引擎** | 0% | 100% | ❌ 完全缺失 |
| **组织中心** | 60% | 100% | 缺少岗位、多维组织 |
| **流程中心** | 0% | 100% | ❌ 完全缺失 |
| **应用中心** | 0% | 100% | ❌ 完全缺失 |
| **工作台** | 0% | 100% | ❌ 完全缺失 |
| **多租户** | 0% | 100% | ❌ 完全缺失 |

---

## 四、推荐实施路线图

### Phase 1: 基础能力完善（1-2个月）
1. 流程中心（待办/已办/草稿）
2. 表单引擎增强（子表、布局）
3. 视图引擎（基础列表视图设计器）

### Phase 2: 核心能力补齐（2-3个月）
4. 工作台门户
5. 应用中心
6. 报表引擎（基础图表）

### Phase 3: 高级能力（3-4个月）
7. 服务编排
8. 脚本规则引擎
9. 流程引擎高级能力

### Phase 4: 生态能力（4-6个月）
10. 多租户
11. 版本管理
12. 资源仓库
13. 集成能力

---

## 五、关键技术选型建议

| 功能 | 推荐技术方案 |
|-----|-------------|
| 脚本引擎 | 后端：Groovy / 前端：JavaScript沙箱 |
| 报表引擎 | 集成 Apache ECharts + 自研报表设计器 |
| 服务编排 | 基于 DAG 的自研执行引擎 |
| 多租户 | 共享数据库+租户ID隔离 |
| 移动端 | UniApp 或 响应式Web |
| 图表可视化 | Apache ECharts |
| 流程图编辑 | bpmn-js 或 X6 |
| 报表表格 | Luckysheet 或 自研 |
