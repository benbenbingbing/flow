package com.workflow.embed.api.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CREATE 表单联动重算请求。
 *
 * <p>请求只携带当前浏览器草稿；实体、表单、Release、记录、用户和上下文坐标
 * 必须由已认证 Embed Session 恢复。</p>
 */
public class EmbedCreateFormEvaluationRequest {

    @NotNull
    @Size(max = 100)
    private Map<String, Object> data;

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? null : new LinkedHashMap<>(data);
    }

    /** 未知顶层字段可能是目标坐标或未来协议漂移，V1 一律拒绝。 */
    @JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException(
                "Embed CREATE form evaluation contains unknown field: " + key);
    }
}
