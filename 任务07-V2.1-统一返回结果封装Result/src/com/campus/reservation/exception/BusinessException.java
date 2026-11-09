package com.campus.reservation.exception;

/** 业务异常父类：统一异常包的公共类型，承载由入口展示的中文提示。 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 保存业务提示，不在异常类中打印或处理流程。 */
    public BusinessException(String message) {
        super(message);
    }
}
