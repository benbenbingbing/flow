package com.workflow.embed.api.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

/** RECORD_CREATE 浏览器请求；实体、表单、Release 和系统字段不属于该协议。 */
public class EmbedRecordCreateRequest {

    @NotNull
    @Size(max = 100)
    private Map<String, Object> data;

    @Size(max = 128)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String clientMutationId;

    private boolean clientMutationIdPresent;

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? null : new LinkedHashMap<>(data);
    }

    public String getClientMutationId() {
        return clientMutationId;
    }

    @JsonSetter("clientMutationId")
    public void setClientMutationId(String clientMutationId) {
        this.clientMutationIdPresent = true;
        this.clientMutationId = clientMutationId;
    }

    public boolean isClientMutationIdPresent() {
        return clientMutationIdPresent;
    }

    /** 全部未知顶层字段直接拒绝，不能被全局 Jackson ignoreUnknown 设置吞掉。 */
    @JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException(
                "Embed RECORD_CREATE 包含未知字段: " + key);
    }
}
