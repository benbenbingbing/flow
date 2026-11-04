package com.workflow.biz.project.embed.demo;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 第三方前端只调用宿主同源接口；不会直接访问 Flow OAuth 或机器 Launch 接口。 */
@RestController
@Profile("partner-embed-demo")
class FlowEmbedController {
    private final FlowEmbedService launches;
    private final PartnerAssertionService assertions;

    FlowEmbedController(FlowEmbedService launches, PartnerAssertionService assertions) {
        this.launches = launches;
        this.assertions = assertions;
    }

    /** 给当前登录会话提供 CSRF header 名和 Token，前端在创建 Launch 时原样带回。 */
    @GetMapping("/partner-api/csrf")
    ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }

    /** 校验浏览器意图后，为服务端认证用户签发一次性 Launch；返回体禁止缓存或写入前端 Storage。 */
    @PostMapping("/partner-api/flow-embed-launch")
    ResponseEntity<FlowEmbedService.Launch> launch(Principal principal, @Valid @RequestBody HostLaunchRequest request) {
        String traceId = UUID.randomUUID().toString();
        var launch = launches.createLaunch(principal.getName(), request, traceId);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .header("X-Trace-Id", traceId).body(launch);
    }

    /** 给 Flow 配置验证公钥；REMOTE_JWKS 必须通过正式 HTTPS 域名访问此路径。 */
    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return assertions.publicJwks();
    }
}
