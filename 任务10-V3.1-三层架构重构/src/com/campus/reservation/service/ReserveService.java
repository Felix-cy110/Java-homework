package com.campus.reservation.service;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.exception.PermissionDeniedException;
import com.campus.reservation.exception.ReserveCancelException;
import com.campus.reservation.exception.ReserveConflictException;
import com.campus.reservation.exception.ReserveRecordNotFoundException;
import com.campus.reservation.exception.ResourceUnavailableException;
import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.dao.ResourceDao;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.campus.reservation.service.BusinessRules.requireAdministrator;
import static com.campus.reservation.service.BusinessRules.requireStudent;
import static com.campus.reservation.service.BusinessRules.validateNotEmpty;
import static com.campus.reservation.service.BusinessRules.hasPassedReservation;
import static com.campus.reservation.service.BusinessRules.findResourceById;

/** 预约业务：申请、查询、审核和取消，协调资源与预约数据访问对象。 */
public class ReserveService implements ReserveOperations {
    private final ResourceDao resourceDao;
    private final ReserveRecordDao reserveDao;

    /** 注入共享 DAO，不缓存第二份资源或预约列表。 */
    public ReserveService(ResourceDao resourceDao, ReserveRecordDao reserveDao) {
        this.resourceDao = resourceDao;
        this.reserveDao = reserveDao;
    }

    /** 学生提交待审核预约，先校验资源状态、时间和 PASS 冲突再持久化。 */
    public Result<ReserveRecord> submitReservation(User user, String resourceId,
            LocalDateTime start, LocalDateTime end,
            String remark) {
        try {
            List<ReserveRecord> records = reserveDao.findAll();
            requireStudent(user, "只有学生可以提交预约申请。");
            Result<CampusResource> found = findResourceById(resourceDao, resourceId);
            if (!found.isSuccess()) {
                return Result.fail("预约失败：" + found.getMsg());
            }
            CampusResource resource = found.getData();
            if (!"AVAILABLE".equals(resource.getStatus())) {
                throw new ResourceUnavailableException();
            }
            validateReservationTime(start, end);
            if (hasOverlappingPassedReservation(records, resourceId, start, end, null)) {
                throw new ReserveConflictException();
            }
            String recordId;
            while (true) {
                String candidateRecordId = UUID.randomUUID().toString();
                boolean exists = Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                        .filter(Objects::nonNull).anyMatch(record -> candidateRecordId.equals(record.getRecordId()));
                if (!exists) {
                    recordId = candidateRecordId;
                    break;
                }
            }
            ReserveRecord record = new ReserveRecord(recordId, resourceId, user.getUsername(),
            LocalDateTime.now(), start, end, ReserveStatus.PENDING,
                    Optional.ofNullable(remark).orElse(""));
            reserveDao.insert(record);
            return Result.success("预约申请提交成功，当前状态为 PENDING：", record);
        } catch (BusinessException e) {
            return Result.fail("预约失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：预约未保存，请检查 reserve.txt 的占用或访问权限。");
        }
    }

    /** 查询当前学生自己的全部预约，禁止通过参数查看他人记录。 */
    public Result<List<ReserveRecord>> queryMyReservations(User user) {
        try {
            List<ReserveRecord> records = reserveDao.findAll();
            requireStudent(user, "只有学生可以查看自己的预约。");
            List<ReserveRecord> mine = Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull)
                    .filter(record -> user.getUsername().equals(record.getApplicantUsername()))
                    .sorted(Comparator.comparing(ReserveRecord::getApplyTime))
                    .collect(Collectors.toList());
            return Result.success("我的预约：", mine);
        } catch (BusinessException e) {
            return Result.fail("查询失败：" + e.getMessage());
        }
    }

    /** 管理员查看全部预约记录。 */
    public Result<List<ReserveRecord>> queryAllReservations(User user) {
        return filterReservations(user, "", "", null);
    }

    /** 使用 Stream 和 Optional 按资源、申请人、状态筛选管理员可见预约。 */
    public Result<List<ReserveRecord>> filterReservations(User user,
            String resourceId, String applicant,
            ReserveStatus status) {
        try {
            List<ReserveRecord> records = reserveDao.findAll();
            requireAdministrator(user);
            String resourceFilter = Optional.ofNullable(resourceId).orElse("").trim();
            String applicantFilter = Optional.ofNullable(applicant).orElse("").trim();
            List<ReserveRecord> filtered = Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull)
                    .filter(record -> resourceFilter.isEmpty()
                            || Optional.ofNullable(record.getResourceId()).map(resourceFilter::equals).orElse(false))
                    .filter(record -> applicantFilter.isEmpty()
                            || Optional.ofNullable(record.getApplicantUsername()).map(applicantFilter::equals).orElse(false))
                    .filter(record -> status == null || status == record.getStatus())
                    .sorted(Comparator.comparing(ReserveRecord::getApplyTime))
                    .collect(Collectors.toList());
            return Result.success("预约筛选结果：", filtered);
        } catch (BusinessException e) {
            return Result.fail("查询失败：" + e.getMessage());
        }
    }

    /** 管理员按记录编号审核，PASS 同步占用资源，REJECT 保持资源状态。 */
    public Result<ReserveRecord> auditReservation(User user, String recordId,
            ReserveStatus decision, String remark) {
        try {
            List<CampusResource> resources = resourceDao.findAll();
            List<ReserveRecord> records = reserveDao.findAll();
            requireAdministrator(user);
            if (!(decision == ReserveStatus.PASS || decision == ReserveStatus.REJECT)) {
                throw new BusinessException("审核结果只能为 PASS 或 REJECT。");
            }
            Result<ReserveRecord> found = findReserveById(recordId);
            if (!found.isSuccess()) {
                return Result.fail("审核失败：" + found.getMsg());
            }
            ReserveRecord existing = found.getData();
            if (existing.getStatus() != ReserveStatus.PENDING) {
                throw new BusinessException("只有 PENDING 状态的预约可以审核。");
            }
            List<ReserveRecord> updatedRecords = new ArrayList<>(records);
            int recordIndex = records.indexOf(existing);
            ReserveRecord changed = new ReserveRecord(existing.getRecordId(), existing.getResourceId(),
                    existing.getApplicantUsername(), existing.getApplyTime(), existing.getReserveStartTime(),
                    existing.getReserveEndTime(), decision,
                    decision == ReserveStatus.REJECT ? Optional.ofNullable(remark).orElse("") : existing.getRemark());
            updatedRecords.set(recordIndex, changed);
            if (decision == ReserveStatus.REJECT) {
                reserveDao.update(changed);
                return Result.success("预约已驳回：", changed);
            }
            if (hasOverlappingPassedReservation(records, existing.getResourceId(), existing.getReserveStartTime(),
                    existing.getReserveEndTime(), existing.getRecordId())) {
                throw new ReserveConflictException();
            }
            Result<CampusResource> resourceResult = findResourceById(resourceDao, existing.getResourceId());
            if (!resourceResult.isSuccess()) {
                return Result.fail("审核失败：" + resourceResult.getMsg());
            }
            CampusResource resource = resourceResult.getData();
            if (!"AVAILABLE".equals(resource.getStatus())) {
                throw new ResourceUnavailableException();
            }
            List<CampusResource> updatedResources = new ArrayList<>(resources);
            int resourceIndex = resources.indexOf(resource);
            CampusResource occupied = new CampusResource(resource.getId(), resource.getName(), resource.getType(),
                    "OCCUPIED", resource.getLocation());
            updatedResources.set(resourceIndex, occupied);
            saveResourcesAndReservations(resources, updatedResources, updatedRecords);
            return Result.success("预约审核通过，资源已变为 OCCUPIED：", changed);
        } catch (BusinessException e) {
            return Result.fail("审核失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：审核结果未完整保存，请检查 resource.txt 和 reserve.txt。");
        }
    }

    /** 学生取消自己的 PENDING/PASS 记录，PASS 取消后无其他通过记录才释放资源。 */
    public Result<ReserveRecord> cancelReservation(User user, String recordId) {
        try {
            List<CampusResource> resources = resourceDao.findAll();
            List<ReserveRecord> records = reserveDao.findAll();
            requireStudent(user, "只有学生可以取消自己的预约。");
            Result<ReserveRecord> found = findReserveById(recordId);
            if (!found.isSuccess()) {
                return Result.fail("取消失败：" + found.getMsg());
            }
            ReserveRecord existing = found.getData();
            if (!user.getUsername().equals(existing.getApplicantUsername())) {
                throw new PermissionDeniedException("学生只能取消自己的预约记录。");
            }
            if (!(existing.getStatus() == ReserveStatus.PENDING || existing.getStatus() == ReserveStatus.PASS)) {
                throw new ReserveCancelException();
            }
            List<ReserveRecord> updatedRecords = new ArrayList<>(records);
            int recordIndex = records.indexOf(existing);
            ReserveRecord changed = new ReserveRecord(existing.getRecordId(), existing.getResourceId(),
                    existing.getApplicantUsername(), existing.getApplyTime(), existing.getReserveStartTime(),
                    existing.getReserveEndTime(), ReserveStatus.CANCEL, existing.getRemark());
            updatedRecords.set(recordIndex, changed);
            if (existing.getStatus() == ReserveStatus.PENDING) {
                reserveDao.update(changed);
                return Result.success("预约已取消：", changed);
            }
            Result<CampusResource> resourceResult = findResourceById(resourceDao, existing.getResourceId());
            if (!resourceResult.isSuccess()) {
                return Result.fail("取消失败：" + resourceResult.getMsg());
            }
            CampusResource resource = resourceResult.getData();
            List<CampusResource> updatedResources = new ArrayList<>(resources);
            int resourceIndex = resources.indexOf(resource);
            CampusResource changedResource = resource;
            if (!hasPassedReservation(updatedRecords, existing.getResourceId())) {
                changedResource = new CampusResource(resource.getId(), resource.getName(), resource.getType(),
                        "AVAILABLE", resource.getLocation());
                updatedResources.set(resourceIndex, changedResource);
            }
            saveResourcesAndReservations(resources, updatedResources, updatedRecords);
            return Result.success("预约已取消，资源状态已同步：", changed);
        } catch (BusinessException e) {
            return Result.fail("取消失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：取消结果未完整保存，请检查 resource.txt 和 reserve.txt。");
        }
    }

    /** 按记录编号查询预约，使用 Stream 和 Optional 统一转换为 Result。 */
    private Result<ReserveRecord> findReserveById(String recordId) {
        try {
            validateNotEmpty(recordId, "预约记录 ID");
            Optional<ReserveRecord> found = reserveDao.findByRecordId(recordId);
            if (found.isPresent()) {
                return Result.success("预约记录查询成功。", found.get());
            }
            throw new ReserveRecordNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 判断同一资源的 PASS 时间段是否重叠，excludeRecordId 用于审核已有记录时排除自身。 */
    private static boolean hasOverlappingPassedReservation(List<ReserveRecord> records, String resourceId,
            LocalDateTime start, LocalDateTime end,
            String excludeRecordId) {
        return Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                .filter(Objects::nonNull)
                .filter(record -> resourceId.equals(record.getResourceId()))
                .filter(record -> record.getStatus() == ReserveStatus.PASS)
                .filter(record -> excludeRecordId == null || !excludeRecordId.equals(record.getRecordId()))
                .anyMatch(record -> start.isBefore(record.getReserveEndTime())
                        && end.isAfter(record.getReserveStartTime()));
    }

    /** 预约时间必须按先后排列，且结束时间不能早于当前时刻。 */
    private static void validateReservationTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new EmptyParameterException("预约时间");
        }
        if (start.isAfter(end)) {
            throw new BusinessException("预约开始时间不能晚于结束时间。");
        }
        if (end.isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约结束时间不能早于当前时间。");
        }
    }

    /** 协调两个 DAO 保存；第二步失败时补偿资源文件和缓存，保持原有失败处理。 */
    private void saveResourcesAndReservations(List<CampusResource> oldResources,
            List<CampusResource> newResources,
            List<ReserveRecord> newRecords) throws IOException {
        boolean resourcesSaved = false;
        try {
            resourceDao.saveAll(newResources);
            resourcesSaved = true;
            reserveDao.saveAll(newRecords);
        } catch (IOException e) {
            if (resourcesSaved) {
                try {
                    resourceDao.saveAll(oldResources);
                } catch (IOException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }
            throw e;
        }
    }
}
