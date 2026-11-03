package com.workflow.contracts.identity.annotation;

import com.workflow.contracts.identity.model.ExtensionMaintenance;
import java.lang.annotation.*;

/** 声明独立表扩展；框架扫描已启用模块后校验并注册，不会触发业务取数。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IdentityExtension {
    String code();
    String title();
    String table();
    String readPermission();
    String writePermission();
    ExtensionMaintenance maintenance() default ExtensionMaintenance.LOCAL;
}
