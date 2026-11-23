package com.workflow.contracts.setting.port;

/** 面向模块的已注册非敏感全局设置读取。 */
public interface GlobalSettingReadPort {
    /** 返回系统有效值的 JSON 文本，包含程序默认值；未知、非公开或敏感键读取失败。 */
    String readPublicSystemValue(String key);
}
