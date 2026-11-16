package com.campus.reservation.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 运行时方法标记：描述操作；可声明敏感参数及未登录时的账号参数。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /** 必填操作描述，由切面读取，不在业务方法中硬编码日志。 */
    String value();
    /** 需要脱敏的参数下标；默认没有，登录与注册显式标记密码。 */
    int[] sensitiveParams() default {};
    /** 未登录操作的账号参数下标；-1 表示使用当前登录用户。 */
    int usernameParam() default -1;
}
