1.每次写完代码后都需要进行单元测试2.项目的总体需求是做通用的流程设计
主要功能
流程设计：可以通过拓哉

---

## 对话记录

### 2026-03-13

**Q:** 了解下这个系统，写个目前系统设计的说明100字以内，简明扼要

**A:** 
通用工作流平台，前后端分离架构。前端基于 Vue3 + Element Plus + Vite，集成 bpmn-js 实现可视化流程设计器；后端采用 Spring Boot 2.7 + Flowable 6.8 + MyBatis-Plus，支持 BPMN 工作流引擎、动态实体表单配置、组织架构及权限管理。实现流程定义、实例驱动、任务审批、业务数据建模等核心能力，可灵活配置业务流程。

---

**Q:** 运行程序

**A:** 
系统已成功启动运行！
- 前端 (Vue3): http://localhost:3000/
- 后端 (Spring Boot): http://localhost:8088/

---

**Q:** 流程设计页面两个问题：1.前进后退没有根据是否可以前进后退enable或者disable。2:查看XML不要输出到控制台，直接弹出窗口格式化展示

**A:** 
已修复两个问题：
1. 前进后退按钮根据 commandStack.canUndo()/canRedo() 状态自动 enable/disable
2. 「查看XML」改为弹窗展示，XML 格式化缩进显示，支持复制到剪贴板

修改文件：workflow-web/src/views/ProcessDesign.vue

---

**Q:** 按钮还是不行，不能点击，你再仔细看下问题，自己试一下，确认好了再跟我反馈

**A:** 
修复了按钮状态更新逻辑：
1. 初始化完成时立即更新状态
2. 导入 XML 完成后更新状态  
3. 撤销/重做操作后更新状态
4. 监听 commandStack changed 事件更新状态

---

**Q:** 前进按钮没问题了，但是回按钮还是不对，新增第一个节点后，返回按钮没有点亮

**A:** 
添加了 `elements.changed` 事件监听作为备用机制，当画布元素变化时强制更新撤销/重做按钮状态。同时保留了 commandStack 的 changed 事件监听。

---

**Q:** 实体绑定流程的时候，没有被绑定的就可以进行绑定，不需要发布后的才能绑定

**A:** 
修改实体绑定流程逻辑：
- 后端新增 `/api/process/unbound` 接口，返回所有未被实体绑定的流程（不限于已发布状态）
- 前端绑定流程时调用新接口，现在可以绑定草稿状态的流程

修改文件：
- workflow-server: ProcessDefinitionConfigMapper.java, ProcessDefinitionService.java, ProcessDefinitionController.java
- workflow-web: process.js, EntityList.vue

---

**Q:** 设计流程节点绑定表单的时候，表单来源采用下拉的方式，并且默认实体表单；选择绑定表单后，下面增加保存按钮，节点配置的tab页中，每个下面都增加一个保存

**A:** 
修改 NodeConfigPanel.vue：
1. 表单来源从 radio-group 改为 el-select 下拉选择
2. 默认值改为 'entity'（实体表单）
3. 每个 tab 页（基本信息、执行人、服务、发送、接收、手动、规则、脚本、调用、条件、动作、表单、高级）底部都添加了保存按钮
4. 添加 saveCurrentTab 方法处理各 tab 的保存逻辑

修改文件：workflow-web/src/components/NodeConfigPanel.vue

---

**Q:** 选择表单，只能选择当前流程绑定实体的表单

**A:** 
修改表单选择逻辑，只允许选择当前流程绑定实体的表单：

后端修改：
1. EntityDefinitionMapper - 新增 findByProcessDefinitionId 方法
2. EntityDefinitionService - 新增 findByProcessDefinitionId 服务方法
3. EntityDefinitionController - 新增 GET /api/entity/process/{processId} 接口

前端修改：
1. NodeConfigPanel.vue - 修改 loadEntityForms 方法：
   - 先通过 processId 获取绑定的实体
   - 再加载该实体的表单列表
2. 表单 tab 中显示所属实体信息

修改文件：
- workflow-server: EntityDefinitionMapper.java, EntityDefinitionService.java, EntityDefinitionController.java
- workflow-web: NodeConfigPanel.vue

---

**Q:** 节点配置 中 执行人指定方式也改成下拉

**A:** 
将执行人配置中的指定方式从 el-radio-group 改为 el-select 下拉选择：
- 选项：固定人员、用户组、角色、表达式、接口动态

修改文件：workflow-web/src/components/NodeConfigPanel.vue

---

**Q:** 实体绑定的流程都没有了，而且点击绑定流程报错 "Process not found: unbound"

**A:** 
修复路由顺序问题：`/{id}` 路由在 `/unbound` 之前定义，导致 "unbound" 被当成流程ID处理。

修改文件：workflow-server/src/main/java/com/workflow/controller/ProcessDefinitionController.java
- 将 `/unbound` 路由定义移到 `/{id}` 路由之前

---

**Q:** 点击绑定流程怎么变成数字了，修改为名称

**A:** 
修复 el-select 显示问题：打开对话框时保留了旧流程ID，但该流程不在"未被绑定"列表中，导致无法匹配 label 而显示数字ID。

修改文件：workflow-web/src/views/EntityList.vue
- 打开绑定对话框时清空 selectedProcessId，让用户重新选择

---

**Q:** 待办消息和已办怎么不显示了

**A:** 
修复 Home.vue 中的导入顺序问题：`watch` 在使用之后才从 vue 导入，导致标签页切换监听失效，无法加载待办/已办列表。

修改文件：workflow-web/src/views/Home.vue
- 将 `watch` 添加到顶部的 import 语句中
- 删除重复导入

---

**Q:** 后端服务有挂掉了，为什么每次实体数据保存发起流程后，就刷新不出数据来

**A:** 
问题原因：MyBatis 配置了 `log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`，SQL 日志直接输出到 System.out。当并发请求或日志量大时，`PrintStream.println` 会阻塞线程，导致服务无响应。

修改文件：
- workflow-server/src/main/resources/application.yml - 将日志实现改为 `org.apache.ibatis.logging.slf4j.Slf4jImpl`
