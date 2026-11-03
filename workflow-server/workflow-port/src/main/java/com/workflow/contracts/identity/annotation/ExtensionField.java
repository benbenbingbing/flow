package com.workflow.contracts.identity.annotation;

import com.workflow.contracts.identity.model.ExtensionControl;
import java.lang.annotation.*;

/** 扩展字段及通用表单描述；列名只供服务端注册，不能由客户端指定。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExtensionField {
    String label();
    String column() default "";
    ExtensionControl control() default ExtensionControl.AUTO;
    boolean required() default false;
    int maxLength() default 255;
    String[] options() default {};
}
