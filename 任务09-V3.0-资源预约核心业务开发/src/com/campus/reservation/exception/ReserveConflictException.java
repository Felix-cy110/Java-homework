package com.campus.reservation.exception;

/** 预约冲突异常：同一资源的已通过时间段发生重叠时拒绝申请。 */
public class ReserveConflictException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 使用统一的时间冲突提示。 */
    public ReserveConflictException() {
        super("该资源在所选时间段已有已通过预约，不能重复预约。");
    }
}
