package com.campus.reservation.entity;

/** 资源统计传输对象：仅承载实时结果，不参与文件持久化。 */
public class ResourceStatDTO {
    private String resourceId;
    private String resourceName;
    private long totalApplyCount;
    private long passCount;
    private long rejectCount;
    private long cancelCount;
    private double passRate;

    /** 创建空统计结果，供调用方设置字段。 */
    public ResourceStatDTO() {
    }

    /** 接收本次聚合快照；计数和比率由服务计算。 */
    public ResourceStatDTO(String resourceId, String resourceName, long totalApplyCount, long passCount,
            long rejectCount, long cancelCount, double passRate) {
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.totalApplyCount = totalApplyCount;
        this.passCount = passCount;
        this.rejectCount = rejectCount;
        this.cancelCount = cancelCount;
        this.passRate = passRate;
    }

    // 访问方法只传递统计字段，不修改业务实体。
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public long getTotalApplyCount() { return totalApplyCount; }
    public void setTotalApplyCount(long totalApplyCount) { this.totalApplyCount = totalApplyCount; }
    public long getPassCount() { return passCount; }
    public void setPassCount(long passCount) { this.passCount = passCount; }
    public long getRejectCount() { return rejectCount; }
    public void setRejectCount(long rejectCount) { this.rejectCount = rejectCount; }
    public long getCancelCount() { return cancelCount; }
    public void setCancelCount(long cancelCount) { this.cancelCount = cancelCount; }
    public double getPassRate() { return passRate; }
    public void setPassRate(double passRate) { this.passRate = passRate; }

    /** 输出全部统计字段，便于调试与核对。 */
    @Override
    public String toString() {
        return "ResourceStatDTO{" + "resourceId=" + resourceId
                + ", resourceName=" + resourceName
                + ", totalApplyCount=" + totalApplyCount
                + ", passCount=" + passCount
                + ", rejectCount=" + rejectCount
                + ", cancelCount=" + cancelCount
                + ", passRate=" + passRate + "}";
    }
}
