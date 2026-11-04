package com.workflow.biz.project.embed.demo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 浏览器只提交本次 SDK 通道和入口意图。
 *
 * @param channelId 必须与本次 SDK mount 使用同一 UUID，避免 Launch 被混入另一 iframe 通道
 * @param entry 用节点保留字段是否出现，以拒绝 LIST/CREATE 中的 recordId:null
 * @param formPresentation 仅影响展示方式，不授予权限；省略或 null 时使用 seamless
 */
public record HostLaunchRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        String channelId,
        @NotNull JsonNode entry,
        @Pattern(regexp = "seamless|dialog") String formPresentation) {
    /** 不依赖宿主全局 ObjectMapper 的宽松策略，未知授权坐标必须在接口边界拒绝。 */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object ignored) {
        throw new IllegalArgumentException("Unsupported host launch field");
    }
}
