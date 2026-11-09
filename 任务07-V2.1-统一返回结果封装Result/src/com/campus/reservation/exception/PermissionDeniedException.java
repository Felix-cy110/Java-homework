package com.campus.reservation.exception;

/** 权限异常：业务方法在读取或修改资源前拒绝越权调用。 */
public class PermissionDeniedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 保存当前操作的中文权限提示。 */
    public PermissionDeniedException(String message) {
        super(message);
    }
}
