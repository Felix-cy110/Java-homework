package com.campus.reservation.entity;

import com.campus.reservation.enums.ReserveStatus;
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
    private String rejectReason;
    private LocalDateTime auditTime;

    /** 创建空记录，供文件解析或调用方通过 setter 填充。 */
    public ReserveRecord() {
    }

    /** 创建未附审核信息的记录，兼容原有申请入口。 */
    public ReserveRecord(String recordId, String resourceId, String applicantUsername,
                         LocalDateTime applyTime, LocalDateTime reserveStartTime,
                         LocalDateTime reserveEndTime, ReserveStatus status, String remark) {
        this(recordId, resourceId, applicantUsername, applyTime, reserveStartTime,
                reserveEndTime, status, remark, "", null);
    }

    /** 使用完整字段创建记录，审核信息与申请备注独立保存。 */
    public ReserveRecord(String recordId, String resourceId, String applicantUsername,
                         LocalDateTime applyTime, LocalDateTime reserveStartTime,
                         LocalDateTime reserveEndTime, ReserveStatus status, String remark,
                         String rejectReason, LocalDateTime auditTime) {
        this.recordId = recordId;
        this.resourceId = resourceId;
        this.applicantUsername = applicantUsername;
        this.applyTime = applyTime;
        this.reserveStartTime = reserveStartTime;
        this.reserveEndTime = reserveEndTime;
        this.status = status;
        this.remark = remark;
        this.rejectReason = rejectReason;
        this.auditTime = auditTime;
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

    /** 驳回原因独立于学生提交的申请备注。 */
    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    /** 未审核或旧文件没有记录审核时间时返回 null。 */
    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    /** 展示状态、申请备注和审核信息，未知的历史时间不伪造。 */
    @Override
    public String toString() {
        return "ReserveRecord{recordId='" + recordId + "', resourceId='" + resourceId
                + "', applicantUsername='" + applicantUsername + "', applyTime=" + applyTime
                + ", reserveStartTime=" + reserveStartTime + ", reserveEndTime=" + reserveEndTime
                + ", status=" + status + ", remark='" + remark
                + "', rejectReason='" + rejectReason + "', auditTime="
                + (auditTime == null ? "未审核或历史未记录" : auditTime) + "}";
    }
}
