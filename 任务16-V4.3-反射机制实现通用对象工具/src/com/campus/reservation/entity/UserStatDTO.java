package com.campus.reservation.entity;

/** 申请人统计传输对象：承载总提交、通过和驳回数量。 */
public class UserStatDTO {
    private String applicantUsername;
    private long totalApplyCount;
    private long passCount;
    private long rejectCount;

    /** 创建空统计结果，供调用方设置字段。 */
    public UserStatDTO() {
    }

    /** 接收本次聚合快照；计数和比率由服务计算。 */
    public UserStatDTO(String applicantUsername, long totalApplyCount, long passCount, long rejectCount) {
        this.applicantUsername = applicantUsername;
        this.totalApplyCount = totalApplyCount;
        this.passCount = passCount;
        this.rejectCount = rejectCount;
    }

    // 访问方法只传递统计字段，不修改业务实体。
    public String getApplicantUsername() { return applicantUsername; }
    public void setApplicantUsername(String applicantUsername) { this.applicantUsername = applicantUsername; }
    public long getTotalApplyCount() { return totalApplyCount; }
    public void setTotalApplyCount(long totalApplyCount) { this.totalApplyCount = totalApplyCount; }
    public long getPassCount() { return passCount; }
    public void setPassCount(long passCount) { this.passCount = passCount; }
    public long getRejectCount() { return rejectCount; }
    public void setRejectCount(long rejectCount) { this.rejectCount = rejectCount; }

    /** 输出全部统计字段，便于调试与核对。 */
    @Override
    public String toString() {
        return "UserStatDTO{" + "applicantUsername=" + applicantUsername
                + ", totalApplyCount=" + totalApplyCount
                + ", passCount=" + passCount
                + ", rejectCount=" + rejectCount + "}";
    }
}
