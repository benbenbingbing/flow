package com.workflow.admin.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改当前登录用户密码的请求。
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 10, max = 72, message = "新密码长度必须为10到72位")
    private String newPassword;
}
