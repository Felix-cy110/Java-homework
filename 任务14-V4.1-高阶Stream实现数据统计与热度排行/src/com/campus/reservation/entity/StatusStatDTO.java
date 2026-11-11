package com.campus.reservation.entity;

import com.campus.reservation.enums.ReserveStatus;


/** 全局状态统计传输对象：包含没有记录时的零计数。 */
public class StatusStatDTO {
    private ReserveStatus status;
    private long count;

    /** 创建空统计结果，供调用方设置字段。 */
    public StatusStatDTO() {
    }

    /** 接收本次聚合快照；计数和比率由服务计算。 */
    public StatusStatDTO(ReserveStatus status, long count) {
        this.status = status;
        this.count = count;
    }

    // 访问方法只传递统计字段，不修改业务实体。
    public ReserveStatus getStatus() { return status; }
    public void setStatus(ReserveStatus status) { this.status = status; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    /** 输出全部统计字段，便于调试与核对。 */
    @Override
    public String toString() {
        return "StatusStatDTO{" + "status=" + status
                + ", count=" + count + "}";
    }
}
