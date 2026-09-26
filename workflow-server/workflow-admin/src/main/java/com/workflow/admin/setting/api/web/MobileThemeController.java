package com.workflow.admin.setting.api.web;

import com.workflow.admin.setting.api.response.MobileThemeView;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.core.result.Result;
import com.workflow.core.security.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开的移动端外观入口，固定读取单个无敏感数据的配置，不接受任意设置键。 */
@RestController
@RequiredArgsConstructor
public class MobileThemeController {
    private final GlobalSettingService service;

    /**
     * 每次刷新均回源读取，保存后无需重新构建、重启或清理客户端缓存。
     *
     * @return 读取后的{@code mobile}{@code theme}结果，供调用方继续处理
     */
    @PublicApi
    @GetMapping("/api/system/mobile-theme")
    public ResponseEntity<Result<MobileThemeView>> read() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(Result.success(service.readMobileTheme()));
    }
}
