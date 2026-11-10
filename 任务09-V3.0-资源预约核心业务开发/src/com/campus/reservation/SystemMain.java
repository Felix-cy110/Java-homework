package com.campus.reservation;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.exception.PasswordErrorException;
import com.campus.reservation.exception.PermissionDeniedException;
import com.campus.reservation.exception.ReserveCancelException;
import com.campus.reservation.exception.ReserveConflictException;
import com.campus.reservation.exception.ReserveRecordNotFoundException;
import com.campus.reservation.exception.ResourceNotFoundException;
import com.campus.reservation.exception.ResourceReservedException;
import com.campus.reservation.exception.ResourceUnavailableException;
import com.campus.reservation.exception.UserAlreadyExistsException;
import com.campus.reservation.exception.UserNotFoundException;
import com.campus.reservation.util.FileUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

/** V3.0 控制台入口：在资源管理基础上加入完整预约业务闭环。 */
public class SystemMain {
    // 按用户要求提供课程默认管理员，仅在账号不存在时用于初始化。
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    /** 加载用户、资源和预约数据，集中处理登录注册与启动错误。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V3.0 ===");
        String loadingFile = "user.txt";
        try (Scanner scanner = new Scanner(System.in)) {
            List<User> users = FileUtil.loadUsers();
            loadingFile = "resource.txt";
            List<CampusResource> resources = FileUtil.loadResources();
            loadingFile = "reserve.txt";
            List<ReserveRecord> records = FileUtil.loadReserveRecords();
            Result<Void> initialization = initializeDefaultAdministrator(users);
            if (!initialization.isSuccess()) {
                System.err.println(initialization.getMsg());
                System.exit(1);
                return;
            }
            while (true) {
                String operation = "";
                try {
                    System.out.println();
                    System.out.println("1.登录  2.注册  0.退出");
                    System.out.print("请选择：");
                    if (!scanner.hasNextLine()) {
                        System.out.println("输入已结束，程序退出。");
                        break;
                    }
                    String choice = scanner.nextLine().trim();
                    if ("0".equals(choice)) {
                        System.out.println("程序已退出。");
                        break;
                    }
                    if (!("1".equals(choice) || "2".equals(choice))) {
                        throw new BusinessException("无效选项，请输入 1、2 或 0。");
                    }

                    boolean registering = "2".equals(choice);
                    operation = registering ? "注册" : "登录";
                    String username = readInput(scanner, "请输入账号：");
                    if (username == null) {
                        break;
                    }
                    validateUsername(username);
                    String password = readInput(scanner, "请输入密码：");
                    if (password == null) {
                        break;
                    }
                    if (registering) {
                        displayResult(registerUser(users, username, password));
                    } else {
                        Result<User> login = loginUser(users, username, password);
                        displayResult(login);
                        if (login.isSuccess()
                                && !runResourceMenu(scanner, login.getData(), resources, records)) {
                            break;
                        }
                    }
                } catch (BusinessException e) {
                    String prefix = operation.isEmpty() ? "" : operation + "失败：";
                    displayResult(Result.fail(prefix + e.getMessage()));
                }
            }
        } catch (IOException e) {
            Result<Void> failure = Result.fail("系统错误：数据加载失败，请检查 " + loadingFile
                    + " 的内容、占用或访问权限后重试。");
            System.err.println(failure.getMsg());
            System.exit(1);
        } catch (RuntimeException e) {
            Result<Void> failure = Result.fail("系统错误：程序运行异常，请重新启动后重试。");
            System.err.println(failure.getMsg());
            System.exit(1);
        }
    }

    /** 只补建缺失的管理员，初始化失败以 Result 返回。 */
    private static Result<Void> initializeDefaultAdministrator(List<User> users) {
        try {
            Result<User> found = findUserByUsername(users, DEFAULT_ADMIN_USERNAME);
            if (found.isSuccess()) {
                if (!"admin".equals(found.getData().getRole())) {
                    throw new BusinessException("默认管理员账号 admin 已被非管理员占用，请先处理 user.txt 中的同名账号；原账号未被覆盖。");
                }
                return Result.success("默认管理员已存在。");
            }
            User administrator = new User(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD,
                    "admin", LocalDateTime.now());
            List<User> updatedUsers = new ArrayList<>(users);
            updatedUsers.add(administrator);
            FileUtil.saveUsers(updatedUsers);
            users.add(administrator);
            return Result.success("默认管理员初始化成功。");
        } catch (BusinessException e) {
            return Result.fail("启动失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：默认管理员初始化失败，请检查 user.txt 的占用或访问权限后重试。");
        }
    }

    /** 统一展示 Result，列表为空时给出通用提示。 */
    private static void displayResult(Result<?> result) {
        System.out.println(result.getMsg());
        if (!result.isSuccess()) {
            return;
        }
        Object data = result.getData();
        if (data instanceof List<?>) {
            List<?> items = (List<?>) data;
            if (items.isEmpty()) {
                System.out.println("暂无符合条件的记录。");
            }
            items.forEach(System.out::println);
        } else if (data instanceof User) {
            System.out.println("用户信息：" + data);
        } else if (data != null) {
            System.out.println(data);
        }
    }

    /** 登录后按角色显示资源和预约菜单，EOF 返回 false。 */
    private static boolean runResourceMenu(Scanner scanner, User user,
                                           List<CampusResource> resources,
                                           List<ReserveRecord> records) {
        while (true) {
            try {
                printMenu(user);
                String choice = readInput(scanner, "请选择：");
                if (choice == null) {
                    return false;
                }
                choice = choice.trim();
                if ("0".equals(choice)) {
                    System.out.println("已退出登录。");
                    return true;
                }
                if ("2".equals(choice)) {
                    String keyword = readInput(scanner, "请输入资源名称关键词（可留空）：");
                    if (keyword == null) {
                        return false;
                    }
                    displayResult(queryResources(user, resources, keyword));
                    continue;
                }
                if ("5".equals(choice)) {
                    if (isAdministrator(user)) {
                        displayResult(queryAllReservations(user, records));
                    } else {
                        displayResult(submitReservationFromInput(scanner, user, resources, records));
                    }
                    continue;
                }
                if ("6".equals(choice)) {
                    if (isAdministrator(user)) {
                        displayResult(auditReservationFromInput(scanner, user, resources, records));
                    } else {
                        displayResult(queryMyReservations(user, records));
                    }
                    continue;
                }
                if ("7".equals(choice)) {
                    if (isAdministrator(user)) {
                        displayResult(filterReservationsFromInput(scanner, user, records));
                    } else {
                        displayResult(cancelReservationFromInput(scanner, user, resources, records));
                    }
                    continue;
                }
                if (isAdministrator(user) && ("1".equals(choice) || "3".equals(choice) || "4".equals(choice))) {
                    handleResourceOperation(scanner, user, resources, records, choice);
                    continue;
                }
                throw new BusinessException("无效菜单选项，请按当前角色菜单输入。");
            } catch (BusinessException e) {
                displayResult(Result.fail("操作失败：" + e.getMessage()));
            }
        }
    }

    /** 输出角色专属菜单，避免学生看到管理员审核入口。 */
    private static void printMenu(User user) {
        if (isAdministrator(user)) {
            System.out.println("管理员菜单：1.新增资源  2.查询资源  3.修改资源  4.删除资源"
                    + "  5.查看全部预约  6.审核预约  7.筛选预约  0.退出登录");
        } else {
            System.out.println("学生菜单：2.查询可预约资源  5.提交预约  6.我的预约"
                    + "  7.取消预约  0.退出登录");
        }
    }

    /** 执行管理员资源新增、修改和删除，业务判断仍在对应 Result 方法中。 */
    private static void handleResourceOperation(Scanner scanner, User user,
                                                List<CampusResource> resources,
                                                List<ReserveRecord> records,
                                                String choice) {
        requireAdministrator(user);
        if ("1".equals(choice)) {
            String name = readRequiredInput(scanner, "请输入资源名称：");
            String type = readRequiredInput(scanner, "请输入资源类型（教室/实验室/设备/场地）：");
            String location = readInput(scanner, "请输入位置（可留空）：");
            displayResult(addResource(user, resources, name, type, location));
            return;
        }
        String id = readRequiredInput(scanner, "请输入资源 ID：");
        if ("4".equals(choice)) {
            displayResult(deleteResource(user, resources, records, id));
            return;
        }
        String name = readRequiredInput(scanner, "请输入资源名称：");
        String type = readRequiredInput(scanner, "请输入资源类型（教室/实验室/设备/场地）：");
        String location = readInput(scanner, "请输入位置（可留空）：");
        String status = readRequiredInput(scanner, "请输入状态（AVAILABLE/OCCUPIED/DISABLED）：");
        displayResult(updateResource(user, resources, id, name, type, location, status));
    }

    /** 从控制台读取预约申请字段，时间格式为 yyyy-MM-ddTHH:mm。 */
    private static Result<ReserveRecord> submitReservationFromInput(Scanner scanner, User user,
                                                                      List<CampusResource> resources,
                                                                      List<ReserveRecord> records) {
        String resourceId = readRequiredInput(scanner, "请输入资源 ID：");
        LocalDateTime start = parseDateTime(readRequiredInput(scanner, "请输入开始时间（yyyy-MM-ddTHH:mm）："), "开始时间");
        LocalDateTime end = parseDateTime(readRequiredInput(scanner, "请输入结束时间（yyyy-MM-ddTHH:mm）："), "结束时间");
        String remark = readInput(scanner, "请输入备注（可留空）：");
        return submitReservation(user, resources, records, resourceId, start, end, remark);
    }

    /** 从控制台读取管理员审核字段，按记录编号选择通过或驳回。 */
    private static Result<ReserveRecord> auditReservationFromInput(Scanner scanner, User user,
                                                                    List<CampusResource> resources,
                                                                    List<ReserveRecord> records) {
        String recordId = readRequiredInput(scanner, "请输入预约记录 ID：");
        String decision = readRequiredInput(scanner, "请输入审核结果（PASS/REJECT）：").toUpperCase(Locale.ROOT);
        ReserveStatus status;
        try {
            status = ReserveStatus.valueOf(decision);
        } catch (IllegalArgumentException e) {
            return Result.fail("操作失败：审核结果只能为 PASS 或 REJECT。");
        }
        String remark = readInput(scanner, "请输入审核备注（可留空）：");
        return auditReservation(user, resources, records, recordId, status, remark);
    }

    /** 从控制台读取筛选条件，空值表示不限制该字段。 */
    private static Result<List<ReserveRecord>> filterReservationsFromInput(Scanner scanner, User user,
                                                                            List<ReserveRecord> records) {
        String resourceId = readInput(scanner, "资源 ID（可留空）：");
        String applicant = readInput(scanner, "申请人账号（可留空）：");
        String statusText = readInput(scanner, "状态（PENDING/PASS/REJECT/CANCEL，可留空）：");
        ReserveStatus status = null;
        if (statusText != null && !statusText.trim().isEmpty()) {
            try {
                status = ReserveStatus.valueOf(statusText.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Result.fail("操作失败：状态只能为 PENDING、PASS、REJECT 或 CANCEL。");
            }
        }
        return filterReservations(user, records, resourceId, applicant, status);
    }

    /** 从控制台读取学生要取消的记录编号。 */
    private static Result<ReserveRecord> cancelReservationFromInput(Scanner scanner, User user,
                                                                     List<CampusResource> resources,
                                                                     List<ReserveRecord> records) {
        String recordId = readRequiredInput(scanner, "请输入要取消的预约记录 ID：");
        return cancelReservation(user, resources, records, recordId);
    }

    /** 读取一行输入；EOF 时由上层结束当前会话。 */
    private static String readInput(Scanner scanner, String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println("输入已结束，程序退出。");
            return null;
        }
        return scanner.nextLine();
    }

    /** 读取不可为空的菜单参数，空值转换为统一业务异常。 */
    private static String readRequiredInput(Scanner scanner, String prompt) {
        String value = readInput(scanner, prompt);
        if (value == null) {
            throw new BusinessException("输入已结束。");
        }
        validateNotEmpty(value, "输入项");
        return value.trim();
    }

    /** 解析控制台时间，统一转换为友好 Result 边界异常。 */
    private static LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldName + "格式错误，请使用 yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-ddTHH:mm。");
        }
    }

    /** 新增资源并在文件保存成功后更新内存集合。 */
    private static Result<CampusResource> addResource(User user, List<CampusResource> resources,
                                                        String name, String type, String location) {
        try {
            requireAdministrator(user);
            validateResourceFields(name, type, location, "AVAILABLE");
            String id;
            while (true) {
                String candidateId = UUID.randomUUID().toString();
                boolean exists = Optional.ofNullable(resources).orElseGet(Collections::emptyList).stream()
                        .filter(Objects::nonNull).anyMatch(resource -> candidateId.equals(resource.getId()));
                if (!exists) {
                    id = candidateId;
                    break;
                }
            }
            CampusResource resource = new CampusResource(id, name, type, "AVAILABLE", location);
            List<CampusResource> updated = new ArrayList<>(resources);
            updated.add(resource);
            FileUtil.saveResources(updated);
            resources.add(resource);
            return Result.success("资源新增成功：", resource);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成，请检查 resource.txt 的占用或访问权限。");
        }
    }

    /** 使用 Stream 组合角色过滤、名称模糊匹配和 ID 排序。 */
    private static Result<List<CampusResource>> queryResources(User user, List<CampusResource> resources,
                                                                String keyword) {
        try {
            requireKnownUser(user);
            String search = Optional.ofNullable(keyword).orElse("").trim().toLowerCase(Locale.ROOT);
            List<CampusResource> visible = Optional.ofNullable(resources).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull)
                    .filter(resource -> isAdministrator(user) || "AVAILABLE".equals(resource.getStatus()))
                    .filter(resource -> search.isEmpty()
                            || Optional.ofNullable(resource.getName()).orElse("")
                            .toLowerCase(Locale.ROOT).contains(search))
                    .sorted(Comparator.comparing(resource -> Optional.ofNullable(resource.getId()).orElse("")))
                    .collect(Collectors.toList());
            return Result.success(isAdministrator(user) ? "全部资源：" : "可预约资源：", visible);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        }
    }

    /** 修改资源基本信息，保存成功后替换内存中的同一条记录。 */
    private static Result<CampusResource> updateResource(User user, List<CampusResource> resources,
                                                           String id, String name, String type,
                                                           String location, String status) {
        try {
            requireAdministrator(user);
            Result<CampusResource> found = findResourceById(resources, id);
            if (!found.isSuccess()) {
                return Result.fail("资源操作失败：" + found.getMsg());
            }
            CampusResource existing = found.getData();
            validateResourceFields(name, type, location, status);
            CampusResource changed = new CampusResource(existing.getId(), name, type, status, location);
            int index = resources.indexOf(existing);
            List<CampusResource> updated = new ArrayList<>(resources);
            updated.set(index, changed);
            FileUtil.saveResources(updated);
            resources.set(index, changed);
            return Result.success("资源修改成功：", changed);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成，请检查 resource.txt 的占用或访问权限。");
        }
    }

    /** 只有存在 PASS 预约时禁止删除，PENDING/REJECT/CANCEL 不阻止删除。 */
    private static Result<Void> deleteResource(User user, List<CampusResource> resources,
                                               List<ReserveRecord> records, String id) {
        try {
            requireAdministrator(user);
            Result<CampusResource> found = findResourceById(resources, id);
            if (!found.isSuccess()) {
                return Result.fail("资源操作失败：" + found.getMsg());
            }
            CampusResource existing = found.getData();
            if (hasPassedReservation(records, existing.getId())) {
                throw new ResourceReservedException();
            }
            List<CampusResource> updated = new ArrayList<>(resources);
            updated.remove(existing);
            FileUtil.saveResources(updated);
            resources.remove(existing);
            return Result.success("资源删除成功。");
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成，请检查 resource.txt 的占用或访问权限。");
        }
    }

    /** 学生提交待审核预约，先校验资源状态、时间和 PASS 冲突再持久化。 */
    private static Result<ReserveRecord> submitReservation(User user, List<CampusResource> resources,
                                                            List<ReserveRecord> records, String resourceId,
                                                            LocalDateTime start, LocalDateTime end,
                                                            String remark) {
        try {
            requireStudent(user, "只有学生可以提交预约申请。");
            Result<CampusResource> found = findResourceById(resources, resourceId);
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
            List<ReserveRecord> updated = new ArrayList<>(records);
            updated.add(record);
            FileUtil.saveReserveRecords(updated);
            records.add(record);
            return Result.success("预约申请提交成功，当前状态为 PENDING：", record);
        } catch (BusinessException e) {
            return Result.fail("预约失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：预约未保存，请检查 reserve.txt 的占用或访问权限。");
        }
    }

    /** 查询当前学生自己的全部预约，禁止通过参数查看他人记录。 */
    private static Result<List<ReserveRecord>> queryMyReservations(User user, List<ReserveRecord> records) {
        try {
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
    private static Result<List<ReserveRecord>> queryAllReservations(User user, List<ReserveRecord> records) {
        return filterReservations(user, records, "", "", null);
    }

    /** 使用 Stream 和 Optional 按资源、申请人、状态筛选管理员可见预约。 */
    private static Result<List<ReserveRecord>> filterReservations(User user, List<ReserveRecord> records,
                                                                   String resourceId, String applicant,
                                                                   ReserveStatus status) {
        try {
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
    private static Result<ReserveRecord> auditReservation(User user, List<CampusResource> resources,
                                                           List<ReserveRecord> records, String recordId,
                                                           ReserveStatus decision, String remark) {
        try {
            requireAdministrator(user);
            if (!(decision == ReserveStatus.PASS || decision == ReserveStatus.REJECT)) {
                throw new BusinessException("审核结果只能为 PASS 或 REJECT。");
            }
            Result<ReserveRecord> found = findReserveById(records, recordId);
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
                FileUtil.saveReserveRecords(updatedRecords);
                records.set(recordIndex, changed);
                return Result.success("预约已驳回：", changed);
            }
            if (hasOverlappingPassedReservation(records, existing.getResourceId(), existing.getReserveStartTime(),
                    existing.getReserveEndTime(), existing.getRecordId())) {
                throw new ReserveConflictException();
            }
            Result<CampusResource> resourceResult = findResourceById(resources, existing.getResourceId());
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
            saveResourcesAndReservations(resources, updatedResources, records, updatedRecords);
            resources.set(resourceIndex, occupied);
            records.set(recordIndex, changed);
            return Result.success("预约审核通过，资源已变为 OCCUPIED：", changed);
        } catch (BusinessException e) {
            return Result.fail("审核失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：审核结果未完整保存，请检查 resource.txt 和 reserve.txt。");
        }
    }

    /** 学生取消自己的 PENDING/PASS 记录，PASS 取消后无其他通过记录才释放资源。 */
    private static Result<ReserveRecord> cancelReservation(User user, List<CampusResource> resources,
                                                            List<ReserveRecord> records, String recordId) {
        try {
            requireStudent(user, "只有学生可以取消自己的预约。");
            Result<ReserveRecord> found = findReserveById(records, recordId);
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
                FileUtil.saveReserveRecords(updatedRecords);
                records.set(recordIndex, changed);
                return Result.success("预约已取消：", changed);
            }
            Result<CampusResource> resourceResult = findResourceById(resources, existing.getResourceId());
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
            saveResourcesAndReservations(resources, updatedResources, records, updatedRecords);
            resources.set(resourceIndex, changedResource);
            records.set(recordIndex, changed);
            return Result.success("预约已取消，资源状态已同步：", changed);
        } catch (BusinessException e) {
            return Result.fail("取消失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：取消结果未完整保存，请检查 resource.txt 和 reserve.txt。");
        }
    }

    /** 按 ID 查询资源，使用 Optional 表达不存在情况。 */
    private static Result<CampusResource> findResourceById(List<CampusResource> resources, String id) {
        try {
            validateNotEmpty(id, "资源 ID");
            Optional<CampusResource> found = Optional.ofNullable(resources).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull).filter(resource -> id.equals(resource.getId())).findFirst();
            if (found.isPresent()) {
                return Result.success("资源查询成功。", found.get());
            }
            throw new ResourceNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 按记录编号查询预约，使用 Stream 和 Optional 统一转换为 Result。 */
    private static Result<ReserveRecord> findReserveById(List<ReserveRecord> records, String recordId) {
        try {
            validateNotEmpty(recordId, "预约记录 ID");
            Optional<ReserveRecord> found = Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull).filter(record -> recordId.equals(record.getRecordId())).findFirst();
            if (found.isPresent()) {
                return Result.success("预约记录查询成功。", found.get());
            }
            throw new ReserveRecordNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 检查资源是否存在仍为 PASS 的预约，用于删除保护和取消后的状态恢复。 */
    private static boolean hasPassedReservation(List<ReserveRecord> records, String resourceId) {
        return Optional.ofNullable(records).orElseGet(Collections::emptyList).stream()
                .filter(Objects::nonNull)
                .anyMatch(record -> resourceId.equals(record.getResourceId())
                        && record.getStatus() == ReserveStatus.PASS);
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

    /** 通过临时副本先保存资源和预约，第二步失败时尽力回滚第一步。 */
    private static void saveResourcesAndReservations(List<CampusResource> oldResources,
                                                     List<CampusResource> newResources,
                                                     List<ReserveRecord> oldRecords,
                                                     List<ReserveRecord> newRecords) throws IOException {
        boolean resourcesSaved = false;
        try {
            FileUtil.saveResources(newResources);
            resourcesSaved = true;
            FileUtil.saveReserveRecords(newRecords);
        } catch (IOException e) {
            if (resourcesSaved) {
                try {
                    FileUtil.saveResources(oldResources);
                } catch (IOException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }
            throw e;
        }
    }

    /** 管理员身份校验，所有管理员操作均在业务边界重复检查。 */
    private static void requireAdministrator(User user) {
        if (!isAdministrator(user)) {
            throw new PermissionDeniedException("只有管理员可以执行该操作。");
        }
    }

    /** 学生身份校验，防止管理员代替学生提交或取消预约。 */
    private static void requireStudent(User user, String message) {
        if (user == null || !"student".equals(user.getRole())) {
            throw new PermissionDeniedException(message);
        }
    }

    /** 只接受系统当前支持的两种角色。 */
    private static void requireKnownUser(User user) {
        if (user == null || !(isAdministrator(user) || "student".equals(user.getRole()))) {
            throw new PermissionDeniedException("请使用有效账号登录后操作。");
        }
    }

    /** 判断用户是否为管理员。 */
    private static boolean isAdministrator(User user) {
        return user != null && "admin".equals(user.getRole());
    }

    /** 校验资源必填项与允许的状态。 */
    private static void validateResourceFields(String name, String type, String location, String status) {
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
    private static void validateResourceType(String type) {
        validateNotEmpty(type, "资源类型");
        if (!("教室".equals(type) || "实验室".equals(type) || "设备".equals(type) || "场地".equals(type))) {
            throw new BusinessException("资源类型只能为教室、实验室、设备或场地。");
        }
    }

    /** 校验注册数据并查重，保存成功后更新内存。 */
    private static Result<User> registerUser(List<User> users, String username, String password) {
        try {
            validateUsername(username);
            validatePassword(password);
            if (findUserByUsername(users, username).isSuccess()) {
                throw new UserAlreadyExistsException();
            }
            User newUser = new User(username, password, "student", LocalDateTime.now());
            List<User> updatedUsers = new ArrayList<>(users);
            updatedUsers.add(newUser);
            FileUtil.saveUsers(updatedUsers);
            users.add(newUser);
            return Result.success("注册成功，请选择登录。", newUser);
        } catch (BusinessException e) {
            return Result.fail("注册失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：用户数据保存失败，账号未创建。");
        }
    }

    /** 检查账号密码并返回登录用户。 */
    private static Result<User> loginUser(List<User> users, String username, String password) {
        try {
            validateUsername(username);
            validateNotEmpty(password, "密码");
            Result<User> found = findUserByUsername(users, username);
            if (!found.isSuccess()) {
                throw new UserNotFoundException();
            }
            User user = found.getData();
            if (!password.equals(user.getPassword())) {
                throw new PasswordErrorException();
            }
            String roleName = isAdministrator(user) ? "管理员" : "学生";
            return Result.success("登录成功，欢迎" + roleName + "！", user);
        } catch (BusinessException e) {
            return Result.fail("登录失败：" + e.getMessage());
        }
    }

    /** 按账号精确查询，使用 Optional 表达不存在状态。 */
    private static Result<User> findUserByUsername(List<User> users, String username) {
        try {
            validateUsername(username);
            Optional<User> found = Optional.ofNullable(users).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull).filter(user -> username.equals(user.getUsername())).findFirst();
            if (found.isPresent()) {
                return Result.success("用户查询成功。", found.get());
            }
            throw new UserNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 账号只做非空校验，保留大小写和前后空格。 */
    private static void validateUsername(String username) {
        validateNotEmpty(username, "账号");
    }

    /** 空密码抛出参数异常，长度不足使用通用业务异常。 */
    private static void validatePassword(String password) {
        validateNotEmpty(password, "密码");
        if (password.length() < 6) {
            throw new BusinessException("密码长度至少为 6 位。");
        }
    }

    /** 拒绝 null、空字符串和仅含常规空白的参数。 */
    private static void validateNotEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new EmptyParameterException(fieldName);
        }
    }
}
