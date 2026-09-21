package com.workflow.admin.setting.api;

/** 登录页可读的固定主题投影，不包含设置记录、账号、版本锁或其他全局设置。 */
public record MobileThemeView(int version, String preset, String primaryColor, String backgroundColor, String surfaceColor) { }
