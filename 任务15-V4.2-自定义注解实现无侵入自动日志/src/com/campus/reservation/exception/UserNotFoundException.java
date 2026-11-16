package com.campus.reservation.exception;

import com.campus.reservation.common.BusinessException;


/** 用户不存在的业务异常，提示登录前先完成注册。 */
public class UserNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 提供统一的账号不存在提示。 */
    public UserNotFoundException() {
        super("用户不存在，请先注册。");
    }
}
