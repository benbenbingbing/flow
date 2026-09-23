package com.workflow.admin.setting.api;

/**
 * 登录页可读的固定主题投影，不包含设置记录、账号、版本锁或其他全局设置。
 *
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param preset {@code preset}，保存在对象中供后续校验、查询或展示
 * @param primaryColor 主要{@code color}，保存在对象中供后续校验、查询或展示
 * @param backgroundColor {@code background}{@code color}，保存在对象中供后续校验、查询或展示
 * @param surfaceColor 界面{@code color}，保存在对象中供后续校验、查询或展示
 */
public record MobileThemeView(int version, String preset, String primaryColor, String backgroundColor, String surfaceColor) { }
