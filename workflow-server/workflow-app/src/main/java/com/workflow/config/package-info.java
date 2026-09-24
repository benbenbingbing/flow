/**
 * 应用级 Spring 装配入口，按数据库、迁移、流程引擎、Web、序列化和调度划分子包。
 * 启动任务放入 bootstrap，跨模块适配放入 adapter，健康检查和指标放入 observability。
 */
package com.workflow.config;
