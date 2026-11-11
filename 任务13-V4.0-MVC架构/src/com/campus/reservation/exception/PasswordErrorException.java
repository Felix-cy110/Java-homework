package com.campus.reservation.exception;

import com.campus.reservation.common.BusinessException;


/** 密码不匹配的业务异常，由登录校验抛出。 */
public class PasswordErrorException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 提供统一的密码错误提示，不回显输入密码。 */
    public PasswordErrorException() {
        super("密码错误。");
    }
}
