package com.campus.reservation.entity;

import java.time.LocalDateTime;

/** 单条操作日志的数据模型；不承担反射、权限或文件读写。 */
public class OperationLogRecord {
    private String logId;
    private String username;
    private LocalDateTime operateTime;
    private String methodFullName;
    private String operationDesc;
    private String params;
    private String resultInfo;
    private String exceptionMsg;

    /** 无参构造，便于逐项装配日志。 */
    public OperationLogRecord() { }

    /** 全参构造，恢复持久化记录时保留原编号与时间。 */
    public OperationLogRecord(String logId, String username, LocalDateTime operateTime, String methodFullName, String operationDesc, String params, String resultInfo, String exceptionMsg) {
        this.logId = logId;
        this.username = username;
        this.operateTime = operateTime;
        this.methodFullName = methodFullName;
        this.operationDesc = operationDesc;
        this.params = params;
        this.resultInfo = resultInfo;
        this.exceptionMsg = exceptionMsg;
    }

    /** 读取 logId 字段。 */
    public String getLogId() { return logId; }
    /** 设置 logId 字段；持久化边界统一校验。 */
    public void setLogId(String logId) { this.logId = logId; }

    /** 读取 username 字段。 */
    public String getUsername() { return username; }
    /** 设置 username 字段；持久化边界统一校验。 */
    public void setUsername(String username) { this.username = username; }

    /** 读取 operateTime 字段。 */
    public LocalDateTime getOperateTime() { return operateTime; }
    /** 设置 operateTime 字段；持久化边界统一校验。 */
    public void setOperateTime(LocalDateTime operateTime) { this.operateTime = operateTime; }

    /** 读取 methodFullName 字段。 */
    public String getMethodFullName() { return methodFullName; }
    /** 设置 methodFullName 字段；持久化边界统一校验。 */
    public void setMethodFullName(String methodFullName) { this.methodFullName = methodFullName; }

    /** 读取 operationDesc 字段。 */
    public String getOperationDesc() { return operationDesc; }
    /** 设置 operationDesc 字段；持久化边界统一校验。 */
    public void setOperationDesc(String operationDesc) { this.operationDesc = operationDesc; }

    /** 读取 params 字段。 */
    public String getParams() { return params; }
    /** 设置 params 字段；持久化边界统一校验。 */
    public void setParams(String params) { this.params = params; }

    /** 读取 resultInfo 字段。 */
    public String getResultInfo() { return resultInfo; }
    /** 设置 resultInfo 字段；持久化边界统一校验。 */
    public void setResultInfo(String resultInfo) { this.resultInfo = resultInfo; }

    /** 读取 exceptionMsg 字段。 */
    public String getExceptionMsg() { return exceptionMsg; }
    /** 设置 exceptionMsg 字段；持久化边界统一校验。 */
    public void setExceptionMsg(String exceptionMsg) { this.exceptionMsg = exceptionMsg; }

    /** 单行显示，控制字符转义，避免日志字段伪造终端中的额外记录。 */
    @Override
    public String toString() {
        return "OperationLogRecord{logId='" + display(logId) + "', username='" + display(username)
                + "', operateTime=" + operateTime + ", methodFullName='" + display(methodFullName)
                + "', operationDesc='" + display(operationDesc) + "', params='" + display(params)
                + "', resultInfo='" + display(resultInfo) + "', exceptionMsg='" + display(exceptionMsg) + "'}";
    }

    /** 仅控制显示形式；实体中的原文本与落盘字段保持完整。 */
    private static String display(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t").replace("\u001B", "\\u001B");
    }
}
