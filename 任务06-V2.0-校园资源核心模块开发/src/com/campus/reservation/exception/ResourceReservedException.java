package com.campus.reservation.exception;

/** 预约关联异常：当前阶段任何预约关联都会阻止删除对应资源。 */
public class ResourceReservedException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 由资源菜单统一展示删除被拒绝的原因。 */
    public ResourceReservedException() {
        super("资源已有预约记录，不能删除。");
    }
}
