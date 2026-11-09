package com.campus.reservation.exception;

/** 资源不存在异常：按 ID 查找失败时交由菜单统一提示。 */
public class ResourceNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 不回显原始输入，直接说明资源不存在。 */
    public ResourceNotFoundException() {
        super("资源不存在，请检查资源 ID。");
    }
}
