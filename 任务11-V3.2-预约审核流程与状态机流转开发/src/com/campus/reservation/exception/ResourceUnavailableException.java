package com.campus.reservation.exception;

/** 资源不可预约异常：资源必须处于 AVAILABLE 状态才能提交申请。 */
public class ResourceUnavailableException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 使用统一的资源状态提示。 */
    public ResourceUnavailableException() {
        super("资源当前不可预约，请选择 AVAILABLE 状态的资源。");
    }
}
