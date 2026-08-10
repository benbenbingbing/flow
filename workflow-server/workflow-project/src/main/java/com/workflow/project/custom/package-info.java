/**
 * 项目级后端扩展示例。
 *
 * <p>该包集中实现平台明确用于按 Bean 名称、编码、类型或集合发现的
 * SPI、Provider、Resolver、Handler、Channel、Connector 与 Strategy，
 * 供前端配置联调和二次开发参考。可安全并存的实现注册为 Spring Bean；
 * 会与平台单例实现冲突的替换示例不注册为 Bean。</p>
 *
 * <p>Mapper、Service 接口以及跨模块读写 Port 属于平台架构边界，
 * 不属于可配置扩展目录，因此不在本包伪造替代实现。</p>
 */
package com.workflow.project.custom;
