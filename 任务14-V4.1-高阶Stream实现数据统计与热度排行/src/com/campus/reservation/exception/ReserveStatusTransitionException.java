package com.campus.reservation.exception;

import com.campus.reservation.common.BusinessException;
import com.campus.reservation.enums.ReserveStatus;

/** 统一拦截重复审核、终态回退及其他非法预约状态流转。 */
public class ReserveStatusTransitionException extends BusinessException {
    private static final long serialVersionUID = 1L;

    /** 显示当前与目标状态，由服务转换为友好失败结果。 */
    public ReserveStatusTransitionException(ReserveStatus current, ReserveStatus target) {
        super("预约状态不能从 " + (current == null ? "未知" : current)
                + " 变为 " + (target == null ? "未知" : target)
                + "；仅待审核预约可通过或驳回，仅待审核或已通过预约可取消。");
    }
}
