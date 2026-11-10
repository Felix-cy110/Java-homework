package com.campus.reservation.exception;

/** 取消异常：只有待审核或已通过预约允许取消。 */
public class ReserveCancelException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 使用统一的状态提示，避免暴露内部实现细节。 */
    public ReserveCancelException() {
        super("只有待审核或已通过的预约可以取消，已驳回或已取消记录不能重复取消。");
    }
}
