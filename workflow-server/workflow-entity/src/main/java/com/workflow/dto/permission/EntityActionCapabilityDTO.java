package com.workflow.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个按钮针对当前数据的运行时能力。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityActionCapabilityDTO {
    private boolean visible;
    private boolean enabled;
    private String reason;

    /**
     * 构造"允许"能力：可见且可用。
     *
     * @return 可见且可用的能力对象
     */
    public static EntityActionCapabilityDTO allowed() {
        return new EntityActionCapabilityDTO(true, true, "");
    }

    /**
     * 构造"隐藏"能力：不可见且不可用。
     *
     * @param reason 隐藏原因（用于调试/提示）
     * @return 不可见且不可用的能力对象
     */
    public static EntityActionCapabilityDTO hidden(String reason) {
        return new EntityActionCapabilityDTO(false, false, reason);
    }

    /**
     * 构造"禁用"能力：可见但不可用。
     *
     * @param reason 禁用原因（用于调试/提示）
     * @return 可见但不可用的能力对象
     */
    public static EntityActionCapabilityDTO disabled(String reason) {
        return new EntityActionCapabilityDTO(true, false, reason);
    }
}
