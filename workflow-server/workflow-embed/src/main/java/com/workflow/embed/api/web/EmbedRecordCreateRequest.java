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
    // 这是通用 JSON 资源上限，不是字段或组件兼容白名单；字段语义由固定
    // Published Form 的标准提交链权威校验。
    @Size(max = 500)
    private Map<String, Object> data;

    @Size(max = 128)
    @Pattern(regexp = "[\\x20-\\x7E]*")
    private String clientMutationId;

    private boolean clientMutationIdPresent;

    @Pattern(regexp = "save|saveAndStart")
    private String actionKey;

    /**
     * 读取数据；查询结果供调用方展示或继续处理。
     *
     * @return 数据键值结果，供调用方继续处理
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * 设置数据；后续读取或执行将使用更新后的状态。
     *
     * @param data 数据，后续用于设置数据并传递处理结果
     */
    public void setData(Map<String, Object> data) {
        this.data = data == null ? null : new LinkedHashMap<>(data);
    }

    /**
     * 读取客户端变更ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的客户端变更ID文本，供调用方比较或展示
     */
    public String getClientMutationId() {
        return clientMutationId;
    }

    /**
     * 设置客户端变更ID；后续读取或执行将使用更新后的状态。
     *
     * @param clientMutationId 客户端变更ID，后续用于设置客户端变更ID时定位或关联目标
     */
    @JsonSetter("clientMutationId")
    public void setClientMutationId(String clientMutationId) {
        this.clientMutationIdPresent = true;
        this.clientMutationId = clientMutationId;
    }

    /**
     * 判断是否客户端变更ID存在；判断结果决定调用方的后续分支。
     *
     * @return 客户端变更ID存在条件成立时为 true，否则为 false
     */
    public boolean isClientMutationIdPresent() {
        return clientMutationIdPresent;
    }

    /**
     * 读取动作键；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的动作键文本，供调用方比较或展示
     */
    public String getActionKey() {
        return actionKey;
    }

    /**
     * 设置动作键；后续读取或执行将使用更新后的状态。
     *
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     */
    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
    }

    /**
     * 全部未知顶层字段直接拒绝，不能被全局 Jackson ignoreUnknown 设置吞掉。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待处理驳回{@code unknown}的原始输入，结果供调用方继续使用
     */
    @JsonAnySetter
    public void rejectUnknown(String key, Object value) {
        throw new IllegalArgumentException(
                "Embed RECORD_CREATE 包含未知字段: " + key);
    }
}
