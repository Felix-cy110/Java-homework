package com.campus.reservation.enums;

/** 资源状态定义；使用 name() 对接既有字符串字段和文件，保持历史数据兼容。 */
public enum ResourceStatus {
    /** 可申请预约。 */
    AVAILABLE,
    /** 已有通过的预约占用。 */
    OCCUPIED,
    /** 管理员停用。 */
    DISABLED
}
