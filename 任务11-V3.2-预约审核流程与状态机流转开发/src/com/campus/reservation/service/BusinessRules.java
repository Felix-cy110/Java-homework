package com.campus.reservation.service;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.exception.PermissionDeniedException;
import com.campus.reservation.exception.ResourceNotFoundException;
import com.campus.reservation.dao.ResourceDao;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 服务内部共享校验；不承担输入输出或文件操作。 */
final class BusinessRules {
    /** 静态校验无需实例化。 */
    private BusinessRules() {
    }

    /** 管理员身份校验，所有管理员操作均在业务边界重复检查。 */
    static void requireAdministrator(User user) {
        if (!isAdministrator(user)) {
            throw new PermissionDeniedException("只有管理员可以执行该操作。");
        }
    }

    /** 学生身份校验，防止管理员代替学生提交或取消预约。 */
    static void requireStudent(User user, String message) {
        if (user == null || !"student".equals(user.getRole())) {
            throw new PermissionDeniedException(message);
        }
    }

    /** 只接受系统当前支持的两种角色。 */
    static void requireKnownUser(User user) {
        if (user == null || !(isAdministrator(user) || "student".equals(user.getRole()))) {
            throw new PermissionDeniedException("请使用有效账号登录后操作。");
        }
    }

    /** 判断用户是否为管理员。 */
    static boolean isAdministrator(User user) {
        return user != null && "admin".equals(user.getRole());
    }

    /** 校验资源必填项与允许的状态。 */
    static void validateResourceFields(String name, String type, String location, String status) {
        validateNotEmpty(name, "资源名称");
        validateResourceType(type);
        if (location == null) {
            throw new EmptyParameterException("位置");
        }
        if (!("AVAILABLE".equals(status) || "OCCUPIED".equals(status) || "DISABLED".equals(status))) {
            throw new BusinessException("资源状态只能为 AVAILABLE、OCCUPIED 或 DISABLED。");
        }
    }

    /** 资源类型采用任务书列出的四种值。 */
    static void validateResourceType(String type) {
        validateNotEmpty(type, "资源类型");
        if (!("教室".equals(type) || "实验室".equals(type) || "设备".equals(type) || "场地".equals(type))) {
            throw new BusinessException("资源类型只能为教室、实验室、设备或场地。");
        }
    }

    /** 账号只做非空校验，保留大小写和前后空格。 */
    static void validateUsername(String username) {
        validateNotEmpty(username, "账号");
    }

    /** 空密码抛出参数异常，长度不足使用通用业务异常。 */
    static void validatePassword(String password) {
        validateNotEmpty(password, "密码");
        if (password.length() < 6) {
            throw new BusinessException("密码长度至少为 6 位。");
        }
    }

    /** 拒绝 null、空字符串和仅含常规空白的参数。 */
    static void validateNotEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new EmptyParameterException(fieldName);
        }
    }

    /** 检查资源是否存在仍为 PASS 的预约，用于删除保护和取消后的状态恢复。 */
    static boolean hasPassedReservation(List<ReserveRecord> records, String resourceId) {
        return Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                .filter(Objects::nonNull)
                .anyMatch(record -> resourceId.equals(record.getResourceId())
                        && record.getStatus() == ReserveStatus.PASS);
    }

    /** 按 ID 查询资源，使用 Optional 表达不存在情况。 */
    static Result<CampusResource> findResourceById(ResourceDao resourceDao, String id) {
        try {
            validateNotEmpty(id, "资源 ID");
            Optional<CampusResource> found = resourceDao.findById(id);
            if (found.isPresent()) {
                return Result.success("资源查询成功。", found.get());
            }
            throw new ResourceNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }
}
