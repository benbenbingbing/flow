package com.workflow.biz.project.embed.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** 示例登录只用于模拟宿主会话；迁入真实系统时沿用其认证与 CSRF 机制。 */
@Configuration
@Profile("partner-embed-demo")
class EmbedDemoSecurity {
    @Bean
    UserDetailsService users(EmbedDemoProperties properties) {
        var host = properties.host();
        return new InMemoryUserDetailsManager(User.withUsername(host.username())
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(host.password()))
                .roles("EMBED_USER").build());
    }

    /** JWKS 公开，Launch 和 CSRF 接口必须登录；保留 CSRF，支持浏览器登录与命令行 Basic 联调。 */
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/.well-known/jwks.json", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) ->
                        writeError(response, mapper, 401, "HOST_LOGIN_REQUIRED")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, mapper, 401, "HOST_LOGIN_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, mapper, 403, "HOST_REQUEST_FORBIDDEN")))
                .build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper,
            int status, String code) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), Map.of("code", status, "errorCode", code));
    }
}
