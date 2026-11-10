package com.campus.reservation.entity;

/** 预约状态枚举：限制预约记录只能处于四种业务状态。 */
public enum ReserveStatus {
    /** 等待管理员审核。 */
    PENDING,
    /** 管理员审核通过。 */
    PASS,
    /** 管理员审核驳回。 */
    REJECT,
    /** 申请人主动取消。 */
    CANCEL
}
