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

    public static EntityActionCapabilityDTO allowed() {
        return new EntityActionCapabilityDTO(true, true, "");
    }

    public static EntityActionCapabilityDTO hidden(String reason) {
        return new EntityActionCapabilityDTO(false, false, reason);
    }

    public static EntityActionCapabilityDTO disabled(String reason) {
        return new EntityActionCapabilityDTO(true, false, reason);
    }
}
