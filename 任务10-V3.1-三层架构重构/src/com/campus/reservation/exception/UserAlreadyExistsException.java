package com.campus.reservation.exception;

/** 账号已存在的业务异常，阻止注册覆盖原用户。 */
public class UserAlreadyExistsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 提供统一的重名提示。 */
    public UserAlreadyExistsException() {
        super("账号已存在。");
    }
}
