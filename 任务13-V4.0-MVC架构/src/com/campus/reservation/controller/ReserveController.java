package com.campus.reservation.controller;

import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.ReserveOperations;
import com.campus.reservation.service.ReserveRecordService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** 预约控制器：转发预约请求，保持原状态机和持久化事务边界。 */
public class ReserveController extends ControllerSupport {
    private final ReserveOperations reserveService;

    /** 获取预约服务单例，不直接获取 DAO。 */
    public ReserveController() throws IOException {
        reserveService = ReserveRecordService.getInstance();
    }

    /** 转发申请，时间冲突和申请人权限由服务判断。 */
    public Result<ReserveRecord> submitReservation(User user, String resourceId,
            LocalDateTime start, LocalDateTime end, String remark) {
        return execute(() -> reserveService.submitReservation(user, resourceId, start, end, remark));
    }

    /** 转发当前学生的预约查询。 */
    public Result<List<ReserveRecord>> queryMyReservations(User user) {
        return execute(() -> reserveService.queryMyReservations(user));
    }

    /** 转发管理员的全部预约查询。 */
    public Result<List<ReserveRecord>> queryAllReservations(User user) {
        return execute(() -> reserveService.queryAllReservations(user));
    }

    /** 转发待审核查询，不在控制器中筛选集合。 */
    public Result<List<ReserveRecord>> queryPendingReservations(User user) {
        return execute(() -> reserveService.queryPendingReservations(user));
    }

    /** 转发可选筛选条件，空条件的含义由服务保留。 */
    public Result<List<ReserveRecord>> filterReservations(User user, String resourceId,
            String applicant, ReserveStatus status) {
        return execute(() -> reserveService.filterReservations(user, resourceId, applicant, status));
    }

    /** 转发审核，不在控制器中更新状态或审核时间。 */
    public Result<ReserveRecord> auditReservation(User user, String recordId,
            ReserveStatus decision, String rejectReason) {
        return execute(() -> reserveService.auditReservation(user, recordId, decision, rejectReason));
    }

    /** 转发取消，保留用户确认的按状态判断规则。 */
    public Result<ReserveRecord> cancelReservation(User user, String recordId) {
        return execute(() -> reserveService.cancelReservation(user, recordId));
    }
}
