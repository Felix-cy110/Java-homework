package com.campus.reservation.service;

import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import java.time.LocalDateTime;
import java.util.List;

/** 预约业务契约：视图依赖此小接口，具体实现负责校验和数据访问。 */
public interface ReserveOperations {
    /** 学生提交待审核预约，先校验资源状态、时间和 PASS 冲突再持久化。 */
    Result<ReserveRecord> submitReservation(User user, String resourceId,
            LocalDateTime start, LocalDateTime end,
            String remark);

    /** 查询当前学生自己的全部预约，禁止通过参数查看他人记录。 */
    Result<List<ReserveRecord>> queryMyReservations(User user);

    /** 管理员查看全部预约记录。 */
    Result<List<ReserveRecord>> queryAllReservations(User user);

    /** 使用 Stream 和 Optional 按资源、申请人、状态筛选管理员可见预约。 */
    Result<List<ReserveRecord>> filterReservations(User user,
            String resourceId, String applicant,
            ReserveStatus status);

    /** 管理员按记录编号审核，PASS 同步占用资源，REJECT 保持资源状态。 */
    Result<ReserveRecord> auditReservation(User user, String recordId,
            ReserveStatus decision, String remark);

    /** 学生取消自己的 PENDING/PASS 记录，PASS 取消后无其他通过记录才释放资源。 */
    Result<ReserveRecord> cancelReservation(User user, String recordId);
}
