package com.campus.reservation.exception;

import com.campus.reservation.common.BusinessException;


/** 预约记录不存在异常：按记录编号查询失败时返回友好提示。 */
public class ReserveRecordNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 使用统一的预约记录不存在提示。 */
    public ReserveRecordNotFoundException() {
        super("预约记录不存在，请检查记录编号。");
    }
}
