package com.campus.reservation.service;

import com.campus.reservation.enums.ResourceStatus;
import com.campus.reservation.dao.DaoFactory;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.entity.ResourceStatDTO;
import com.campus.reservation.entity.UserStatDTO;
import com.campus.reservation.entity.StatusStatDTO;
import com.campus.reservation.common.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.exception.PermissionDeniedException;
import com.campus.reservation.exception.ReserveStatusTransitionException;
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
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.campus.reservation.service.BusinessRules.requireAdministrator;
import static com.campus.reservation.service.BusinessRules.requireStudent;
import static com.campus.reservation.service.BusinessRules.validateNotEmpty;
import static com.campus.reservation.service.BusinessRules.hasPassedReservation;
import static com.campus.reservation.service.BusinessRules.findResourceById;

/** 预约业务及实时统计：协调资源和预约 DAO，报表仅聚合内存快照。 */
public class ReserveRecordService implements ReserveOperations {
    private static final ReserveRecordService INSTANCE;
    private static final IOException INITIALIZATION_ERROR;

    // 构建唯一服务，并保留 DAO 初始化错误供启动入口处理。
    static {
        ReserveRecordService instance = null;
        IOException error = null;
        try {
            instance = new ReserveRecordService();
        } catch (IOException e) {
            error = e;
        }
        INSTANCE = instance;
        INITIALIZATION_ERROR = error;
    }
    private final ResourceDao resourceDao;
    private final ReserveRecordDao reserveDao;

    /** 通过工厂绑定共享 DAO。 */
    private ReserveRecordService() throws IOException {
        this.resourceDao = DaoFactory.getResourceDao();
        this.reserveDao = DaoFactory.getReserveRecordDao();
    }

    /** 获取唯一服务实例。 */
    public static ReserveRecordService getInstance() throws IOException {
        if (INITIALIZATION_ERROR != null) {
            throw INITIALIZATION_ERROR;
        }
        return INSTANCE;
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
            if (!ResourceStatus.AVAILABLE.name().equals(resource.getStatus())) {
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

    /** 复用管理员权限和 Stream 筛选，仅返回待审核项。 */
    public Result<List<ReserveRecord>> queryPendingReservations(User user) {
        return filterReservations(user, "", "", ReserveStatus.PENDING);
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

    /** 统一校验流转并记录审核信息，PASS 同步占用资源。 */
    public Result<ReserveRecord> auditReservation(User user, String recordId,
            ReserveStatus decision, String rejectReason) {
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
            validateTransition(existing.getStatus(), decision);
            List<ReserveRecord> updatedRecords = new ArrayList<>(records);
            int recordIndex = records.indexOf(existing);
            ReserveRecord changed = new ReserveRecord(existing.getRecordId(), existing.getResourceId(),
                    existing.getApplicantUsername(), existing.getApplyTime(), existing.getReserveStartTime(),
                    existing.getReserveEndTime(), decision, existing.getRemark(),
                    decision == ReserveStatus.REJECT ? Optional.ofNullable(rejectReason).orElse("") : "",
                    LocalDateTime.now());
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
            if (!ResourceStatus.AVAILABLE.name().equals(resource.getStatus())) {
                throw new ResourceUnavailableException();
            }
            List<CampusResource> updatedResources = new ArrayList<>(resources);
            int resourceIndex = resources.indexOf(resource);
            CampusResource occupied = new CampusResource(resource.getId(), resource.getName(), resource.getType(),
                    ResourceStatus.OCCUPIED.name(), resource.getLocation());
            updatedResources.set(resourceIndex, occupied);
            saveResourcesAndReservations(resources, updatedResources, updatedRecords);
            return Result.success("预约审核通过，资源已变为 OCCUPIED：", changed);
        } catch (BusinessException e) {
            return Result.fail("审核失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：审核结果未完整保存，请检查 resource.txt 和 reserve.txt。");
        }
    }

    /** 按已确认口径只检查归属和状态；取消保留审核信息，不增加时间门槛。 */
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
            validateTransition(existing.getStatus(), ReserveStatus.CANCEL);
            List<ReserveRecord> updatedRecords = new ArrayList<>(records);
            int recordIndex = records.indexOf(existing);
            ReserveRecord changed = new ReserveRecord(existing.getRecordId(), existing.getResourceId(),
                    existing.getApplicantUsername(), existing.getApplyTime(), existing.getReserveStartTime(),
                    existing.getReserveEndTime(), ReserveStatus.CANCEL, existing.getRemark(),
                    existing.getRejectReason(), existing.getAuditTime());
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
                        ResourceStatus.AVAILABLE.name(), resource.getLocation());
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

    /** 管理员按资源查看全部状态的数量；已删除资源保留历史统计。 */
    @Override
    public Result<List<ResourceStatDTO>> queryResourceStatistics(User user) {
        try {
            requireAdministrator(user);
            return Result.success("资源预约统计报表：", aggregateResourceStatistics());
        } catch (BusinessException e) {
            return Result.fail("统计失败：" + e.getMessage());
        }
    }

    /** 每次重新聚合并按通过数降序截取，条数必须为正整数。 */
    @Override
    public Result<List<ResourceStatDTO>> queryHotResources(User user, int topN) {
        try {
            requireAdministrator(user);
            if (topN <= 0) {
                throw new BusinessException("TOP 条数必须为正整数。");
            }
            List<ResourceStatDTO> top = aggregateResourceStatistics().stream()
                    .sorted(Comparator.comparingLong(ResourceStatDTO::getPassCount).reversed()
                            .thenComparing(ResourceStatDTO::getResourceId))
                    .limit(topN)
                    .collect(Collectors.toList());
            return Result.success("热门资源 TOP " + topN + "：", top);
        } catch (BusinessException e) {
            return Result.fail("统计失败：" + e.getMessage());
        }
    }

    /** 按申请人分组；总提交数包含待审、通过、驳回及取消。 */
    @Override
    public Result<List<UserStatDTO>> queryUserStatistics(User user) {
        try {
            requireAdministrator(user);
            List<UserStatDTO> statistics = reservationStream()
                    .collect(Collectors.groupingBy(ReserveRecord::getApplicantUsername))
                    .entrySet().stream()
                    .map(entry -> {
                        List<ReserveRecord> records = entry.getValue();
                        Map<ReserveStatus, Long> counts = countStatuses(records);
                        long total = records.stream().collect(Collectors.counting());
                        return new UserStatDTO(entry.getKey(), total,
                                counts.getOrDefault(ReserveStatus.PASS, 0L),
                                counts.getOrDefault(ReserveStatus.REJECT, 0L));
                    })
                    .sorted(Comparator.comparing(UserStatDTO::getApplicantUsername))
                    .collect(Collectors.toList());
            return Result.success("用户预约统计报表：", statistics);
        } catch (BusinessException e) {
            return Result.fail("统计失败：" + e.getMessage());
        }
    }

    /** 固定展示四种状态，缺失的分组补零，不缓存查询结果。 */
    @Override
    public Result<List<StatusStatDTO>> queryStatusStatistics(User user) {
        try {
            requireAdministrator(user);
            Map<ReserveStatus, Long> counts = reservationStream()
                    .collect(Collectors.groupingBy(ReserveRecord::getStatus, Collectors.counting()));
            List<StatusStatDTO> statistics = Arrays.stream(ReserveStatus.values())
                    .map(status -> new StatusStatDTO(status, counts.getOrDefault(status, 0L)))
                    .collect(Collectors.toList());
            return Result.success("全局预约状态汇总：", statistics);
        } catch (BusinessException e) {
            return Result.fail("统计失败：" + e.getMessage());
        }
    }

    /** 查询当前 DAO 快照，空集合和空元素不会进入分组管道。 */
    private Stream<ReserveRecord> reservationStream() {
        return Optional.ofNullable(reserveDao.findAll()).orElseGet(Collections::emptyList)
                .stream().filter(Objects::nonNull);
    }

    /** 用分组收集器统一计数，避免各报表维护手工计数器。 */
    private Map<ReserveStatus, Long> countStatuses(List<ReserveRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(ReserveRecord::getStatus, Collectors.counting()));
    }

    /** 只为有预约的资源生成 DTO，名称缺失时保留资源 ID 和历史记录。 */
    private List<ResourceStatDTO> aggregateResourceStatistics() {
        List<CampusResource> resources = Optional.ofNullable(resourceDao.findAll())
                .orElseGet(Collections::emptyList);
        return reservationStream().collect(Collectors.groupingBy(ReserveRecord::getResourceId))
                .entrySet().stream()
                .map(entry -> {
                    List<ReserveRecord> records = entry.getValue();
                    Map<ReserveStatus, Long> counts = countStatuses(records);
                    long total = records.stream().collect(Collectors.counting());
                    long pass = counts.getOrDefault(ReserveStatus.PASS, 0L);
                    // 使用浮点除法；没有申请时通过率定义为零。
                    double rate = total == 0 ? 0.0 : (double) pass / total;
                    String name = resources.stream().filter(Objects::nonNull)
                            .filter(resource -> entry.getKey().equals(resource.getId())).findFirst()
                            .map(CampusResource::getName).orElse("资源已删除");
                    return new ResourceStatDTO(entry.getKey(), name, total, pass,
                            counts.getOrDefault(ReserveStatus.REJECT, 0L),
                            counts.getOrDefault(ReserveStatus.CANCEL, 0L), rate);
                })
                .sorted(Comparator.comparing(ResourceStatDTO::getResourceId))
                .collect(Collectors.toList());
    }

    /** 审核与取消都经过枚举规则，空状态也转为业务失败。 */
    private static void validateTransition(ReserveStatus current, ReserveStatus target) {
        if (!Optional.ofNullable(current).map(status -> status.canTransitionTo(target)).orElse(false)) {
            throw new ReserveStatusTransitionException(current, target);
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
