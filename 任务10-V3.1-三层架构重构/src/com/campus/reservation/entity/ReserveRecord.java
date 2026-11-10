package com.campus.reservation.entity;

import java.time.LocalDateTime;

/** 预约记录实体：只承载预约字段，不包含校验、持久化或权限逻辑。 */
public class ReserveRecord {
    private String recordId;
    private String resourceId;
    private String applicantUsername;
    private LocalDateTime applyTime;
    private LocalDateTime reserveStartTime;
    private LocalDateTime reserveEndTime;
    private ReserveStatus status;
    private String remark;

    /** 创建空记录，供文件解析或调用方通过 setter 填充。 */
    public ReserveRecord() {
    }

    /** 使用完整预约字段创建记录，状态由调用方明确传入。 */
    public ReserveRecord(String recordId, String resourceId, String applicantUsername,
                         LocalDateTime applyTime, LocalDateTime reserveStartTime,
                         LocalDateTime reserveEndTime, ReserveStatus status, String remark) {
        this.recordId = recordId;
        this.resourceId = resourceId;
        this.applicantUsername = applicantUsername;
        this.applyTime = applyTime;
        this.reserveStartTime = reserveStartTime;
        this.reserveEndTime = reserveEndTime;
        this.status = status;
        this.remark = remark;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getApplicantUsername() {
        return applicantUsername;
    }

    public void setApplicantUsername(String applicantUsername) {
        this.applicantUsername = applicantUsername;
    }

    public LocalDateTime getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(LocalDateTime applyTime) {
        this.applyTime = applyTime;
    }

    public LocalDateTime getReserveStartTime() {
        return reserveStartTime;
    }

    public void setReserveStartTime(LocalDateTime reserveStartTime) {
        this.reserveStartTime = reserveStartTime;
    }

    public LocalDateTime getReserveEndTime() {
        return reserveEndTime;
    }

    public void setReserveEndTime(LocalDateTime reserveEndTime) {
        this.reserveEndTime = reserveEndTime;
    }

    public ReserveStatus getStatus() {
        return status;
    }

    public void setStatus(ReserveStatus status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /** 展示完整预约字段，便于控制台核对状态联动结果。 */
    @Override
    public String toString() {
        return "ReserveRecord{recordId='" + recordId + "', resourceId='" + resourceId
                + "', applicantUsername='" + applicantUsername + "', applyTime=" + applyTime
                + ", reserveStartTime=" + reserveStartTime + ", reserveEndTime=" + reserveEndTime
                + ", status=" + status + ", remark='" + remark + "'}";
    }
}
