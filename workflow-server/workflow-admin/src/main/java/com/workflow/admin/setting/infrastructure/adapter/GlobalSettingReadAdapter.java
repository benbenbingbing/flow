package com.workflow.admin.setting.infrastructure.adapter;

import com.workflow.admin.setting.application.GlobalSettingRegistry;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.port.system.SysGlobalSettingPort;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 公开给业务模块的设置先经注册表核验，避免通用读取能力暴露内部密钥。 */
@Component
@RequiredArgsConstructor
public class GlobalSettingReadAdapter implements SysGlobalSettingPort {
    private final GlobalSettingRegistry registry;
    private final GlobalSettingService settings;

    @Override
    public String getPublicSetting(String key) {
        var definition = registry.require(key);
        if (!definition.clientReadable() || definition.sensitive()) {
            throw new ForbiddenException("设置项不对业务模块开放");
        }
        return settings.readSystemValue(key).toString();
    }
}
