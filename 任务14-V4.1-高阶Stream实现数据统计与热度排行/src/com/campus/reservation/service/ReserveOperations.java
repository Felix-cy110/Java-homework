package com.campus.reservation.service;

import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.entity.ResourceStatDTO;
import com.campus.reservation.entity.UserStatDTO;
import com.campus.reservation.entity.StatusStatDTO;
import java.time.LocalDateTime;
import java.util.List;

/** 预约及统计契约：控制器依赖此接口，服务实现负责校验、聚合和数据访问。 */
public interface ReserveOperations {
    /**
     * 学生提交待审核预约，先校验资源状态、时间和 PASS 冲突再持久化。
     *
     * @param user 当前申请人，用于校验学生身份
     * @param resourceId 预约的资源编号
     * @param start 预约开始时间
     * @param end 预约结束时间
     * @param remark 申请备注
     */
    Result<ReserveRecord> submitReservation(User user, String resourceId,
            LocalDateTime start, LocalDateTime end,
            String remark);

    /**
     * 查询当前学生自己的全部预约，禁止通过参数查看他人记录。
     *
     * @param user 当前学生，用于校验身份和确定记录归属
     */
    Result<List<ReserveRecord>> queryMyReservations(User user);

    /**
     * 管理员查看全部预约记录。
     *
     * @param user 当前操作用户，用于校验管理员权限
     */
    Result<List<ReserveRecord>> queryAllReservations(User user);

    /**
     * 管理员仅查看等待审核的预约。
     *
     * @param user 当前操作用户，用于校验管理员权限
     */
    Result<List<ReserveRecord>> queryPendingReservations(User user);

    /**
     * 使用 Stream 和 Optional 按资源、申请人、状态筛选管理员可见预约。
     *
     * @param user 当前操作用户，用于校验管理员权限
     * @param resourceId 资源编号筛选条件，为空时不筛选
     * @param applicant 申请人账号筛选条件，为空时不筛选
     * @param status 预约状态筛选条件，为 null 时不筛选
     */
    Result<List<ReserveRecord>> filterReservations(User user,
            String resourceId, String applicant,
            ReserveStatus status);

    /**
     * 管理员审核并记录时间，驳回原因不覆盖申请备注。
     *
     * @param user 当前操作用户，用于校验管理员权限
     * @param recordId 要审核的预约记录编号
     * @param decision 审核结果，取 PASS 或 REJECT
     * @param rejectReason 驳回原因
     */
    Result<ReserveRecord> auditReservation(User user, String recordId,
            ReserveStatus decision, String rejectReason);

    /**
     * 学生取消自己的 PENDING/PASS 记录，PASS 取消后无其他通过记录才释放资源。
     *
     * @param user 当前学生，用于校验身份和记录归属
     * @param recordId 要取消的预约记录编号
     */
    Result<ReserveRecord> cancelReservation(User user, String recordId);

    /** 管理员按资源汇总预约数量与通过率。 */
    Result<List<ResourceStatDTO>> queryResourceStatistics(User user);

    /** 管理员按通过数量降序查询前 N 个资源。 */
    Result<List<ResourceStatDTO>> queryHotResources(User user, int topN);

    /** 管理员按申请人汇总总提交、通过和驳回数量。 */
    Result<List<UserStatDTO>> queryUserStatistics(User user);

    /** 管理员查询全部四种状态的实时总数。 */
    Result<List<StatusStatDTO>> queryStatusStatistics(User user);
}
