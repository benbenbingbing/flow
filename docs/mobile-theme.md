# 移动端系统主题

在 PC 的「系统管理 → 全局设置 → 移动端主题」配置系统统一配色。

- 预设：青绿、经典蓝、雅紫、暖橙、玫瑰红、湖青、石墨灰；选预设后也可继续自定义。
- 可编辑主色、页面背景、内容背景，右侧即时预览。第一版支持浅色背景。
- 点击「保存主题」后，移动端刷新页面即可生效，无需重新构建、重启服务或清除缓存。
- 点击「恢复程序默认值」后，移动端刷新恢复青绿。
- 不支持个人覆盖；不会修改 PC 主题。成功、警告、驳回等业务状态色独立于品牌色。
- 已打开的移动页面保持现有主题，刷新后更新。接口异常或超时（4 秒）使用默认主题，保证可以登录。

## 存储与读取

复用全局设置键 `ui.mobile.theme`，作用域为 `SYSTEM`，所有者为 `0`，逻辑类型为 `JSON`。
数据库现有文本列存储 JSON 字符串，不依赖数据库 JSON 类型，也不新增迁移。
未保存时由设置注册表提供默认值，第一次保存时写入记录。

```json
{
  "version": 1,
  "preset": "green",
  "primaryColor": "#196B62",
  "backgroundColor": "#F4F7F6",
  "surfaceColor": "#FFFFFF"
}
```

只允许这五个字段，颜色使用 `#RRGGBB`，`preset` 为 `green`、`blue`、`purple`、`orange`、`rose`、`cyan`、`slate` 或 `custom`。
颜色与预设不匹配时自动标记为 `custom`，实际效果始终采用保存的颜色。

登录前可访问 `GET /api/system/mobile-theme`，响应仅投影上述五个主题字段，不接受任意设置键。
接口逐次读库并返回 `Cache-Control: no-store`；移动端启动通过 `fetch` 的 `cache: 'no-store'` 读取，挂载前应用。
部署时将移动端 `/api` 代理到后端，网关不要覆盖该接口的禁止缓存头。
写入继续使用现有全局设置接口、`system:setting:manage` 权限、版本冲突检查和审计。

## 代码边界

- `packages/workflow-core/src/shared/mobile-theme.js`：纯配置协议、预设、校验及颜色派生；PC 编辑器和移动端共用。
- `workflow-mobile/src/theme/index.js`：`applyTheme(config)`、`resetTheme()` 和运行时读取，只作用于移动文档。
- `packages/workflow-mobile-ui/src/styles/theme.css`：组件变量的默认值；组件使用语义变量，不写死品牌色。
- `workflow-web/src/views/system/components/MobileThemeEditor.vue`：可视化编辑器；只给预览容器设置变量，不给 PC 根节点赋值。
- `workflow-admin` 的 `MobileThemeController`：只读公开主题接口；设置服务负责白名单校验与系统级持久化。

移动端将变量同时设到 `html` 和 `body`，Vant 挂载到 body 的弹窗也会继承主题。
按钮文字按主色亮度选择黑/白；链接及浅底强调文字派生较深颜色保证可读性。
新增页面、业务扩展和流程图高亮均应使用 `--flow-mobile-*` 语义变量。

本功能首次上线需要发布新代码；之后只修改主题 JSON，无需构建。

配色参考：[配色卡的网页配色](https://www.peiseka.com/webpeise.html)的同色相搭配思路；玫瑰主色参考[配色卡 No.2539](https://peiseka.com/index-index-peise-id-2539.html) 的 `#B82F61`，其余配色按移动界面的可读性调整明度。

## 本次验证

- shared packages、PC 主应用/Embed、移动端以及后端构建通过。
- 后端全局设置与主题相关测试 71 项通过；前端主题/移动单元测试 9 项通过；移动端浏览器回归 13 项通过。
- 本机真实后台保存经典蓝、湖青和自定义玫瑰主色；移动端刷新读取新颜色，期间没有重新构建。
- 校验非法颜色禁止提交，PC 根节点没有移动端变量污染，匿名主题响应为 `no-store`。
- 验证后恢复程序默认青绿。未新增、修改或删除任何数据库迁移文件。
