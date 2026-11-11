package com.campus.reservation.enums;

/** 预约状态及合法后继；审核和取消共用同一份流转规则。 */
public enum ReserveStatus {
    /** 等待管理员审核。 */
    PENDING,
    /** 管理员审核通过。 */
    PASS,
    /** 管理员审核驳回。 */
    REJECT,
    /** 申请人主动取消。 */
    CANCEL;

    /** 仅开放四条合法路径，终态、重复操作和空目标均不可流转。 */
    public boolean canTransitionTo(ReserveStatus target) {
        return (this == PENDING && (target == PASS || target == REJECT || target == CANCEL))
                || (this == PASS && target == CANCEL);
    }
}
