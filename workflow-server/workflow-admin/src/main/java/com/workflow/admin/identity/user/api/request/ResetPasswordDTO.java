package com.workflow.admin.identity.user.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Administrator-supplied password reset request. The value is write-only and
 * is never included in an API response or audit payload.
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 10, max = 72, message = "新密码长度必须为10到72位")
    private String newPassword;
}
