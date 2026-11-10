package com.campus.reservation.ui.console;

import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.service.UserOperations;
import com.campus.reservation.service.ResourceOperations;
import com.campus.reservation.service.ReserveOperations;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** 控制台视图：收集和转换输入、路由菜单、展示 Result，不访问 DAO 或文件。 */
public class ConsoleUI {
    private final UserOperations userService;
    private final ResourceOperations resourceService;
    private final ReserveOperations reserveService;

    /** 注入三个独立业务接口，UI 不创建或依赖其具体实现。 */
    public ConsoleUI(UserOperations userService, ResourceOperations resourceService,
                     ReserveOperations reserveService) {
        this.userService = userService;
        this.resourceService = resourceService;
        this.reserveService = reserveService;
    }

    /** 展示版本标题，启动失败时也可看到当前版本。 */
    public static void printBanner() {
        System.out.println("=== 校园资源预约系统 V3.1 ===");
    }

    /** 只展示启动错误文本，不输出底层堆栈。 */
    public static void showStartupError(String message) {
        System.err.println(message);
    }

    /** 执行登录注册循环，返回进程退出码；Scanner 由视图关闭。 */
    public int run() {
        try (Scanner scanner = new Scanner(System.in)) {
            Result<Void> initialization = userService.initializeDefaultAdministrator();
            if (!initialization.isSuccess()) {
                showStartupError(initialization.getMsg());
                return 1;
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
                    Result<Void> check = userService.checkUsername(username);
                    if (!check.isSuccess()) {
                        displayResult(Result.fail(operation + "失败：" + check.getMsg()));
                        continue;
                    }
                    String password = readInput(scanner, "请输入密码：");
                    if (password == null) {
                        break;
                    }
                    if (registering) {
                        displayResult(userService.registerUser(username, password));
                    } else {
                        Result<User> login = userService.loginUser(username, password);
                        displayResult(login);
                        if (login.isSuccess() && !runResourceMenu(scanner, login.getData())) {
                            break;
                        }
                    }
                } catch (BusinessException e) {
                    String prefix = operation.isEmpty() ? "" : operation + "失败：";
                    displayResult(Result.fail(prefix + e.getMessage()));
                }
            }
            return 0;
        }
    }

    /** 统一展示 Result，列表为空时给出通用提示。 */
    private void displayResult(Result<?> result) {
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
    private boolean runResourceMenu(Scanner scanner, User user) {
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
                    displayResult(resourceService.queryResources(user, keyword));
                    continue;
                }
                if ("5".equals(choice)) {
                    if (isAdministrator(user)) {
                        displayResult(reserveService.queryAllReservations(user));
                    } else {
                        displayResult(submitReservationFromInput(scanner, user));
                    }
                    continue;
                }
                if ("6".equals(choice)) {
                    if (isAdministrator(user)) {
                        displayResult(auditReservationFromInput(scanner, user));
                    } else {
                        displayResult(reserveService.queryMyReservations(user));
                    }
                    continue;
                }
                if ("7".equals(choice)) {
                    if (isAdministrator(user)) {
                        displayResult(filterReservationsFromInput(scanner, user));
                    } else {
                        displayResult(cancelReservationFromInput(scanner, user));
                    }
                    continue;
                }
                if (isAdministrator(user) && ("1".equals(choice) || "3".equals(choice) || "4".equals(choice))) {
                    handleResourceOperation(scanner, user, choice);
                    continue;
                }
                throw new BusinessException("无效菜单选项，请按当前角色菜单输入。");
            } catch (BusinessException e) {
                displayResult(Result.fail("操作失败：" + e.getMessage()));
            }
        }
    }

    /** 输出角色专属菜单，避免学生看到管理员审核入口。 */
    private void printMenu(User user) {
        if (isAdministrator(user)) {
            System.out.println("管理员菜单：1.新增资源  2.查询资源  3.修改资源  4.删除资源"
                    + "  5.查看全部预约  6.审核预约  7.筛选预约  0.退出登录");
        } else {
            System.out.println("学生菜单：2.查询可预约资源  5.提交预约  6.我的预约"
                    + "  7.取消预约  0.退出登录");
        }
    }

    /** 收集资源字段并调用服务，视图不修改实体或集合。 */
    private void handleResourceOperation(Scanner scanner, User user,
            String choice) {
        if ("1".equals(choice)) {
            String name = readRequiredInput(scanner, "请输入资源名称：");
            String type = readRequiredInput(scanner, "请输入资源类型（教室/实验室/设备/场地）：");
            String location = readInput(scanner, "请输入位置（可留空）：");
            displayResult(resourceService.addResource(user, name, type, location));
            return;
        }
        String id = readRequiredInput(scanner, "请输入资源 ID：");
        if ("4".equals(choice)) {
            displayResult(resourceService.deleteResource(user, id));
            return;
        }
        String name = readRequiredInput(scanner, "请输入资源名称：");
        String type = readRequiredInput(scanner, "请输入资源类型（教室/实验室/设备/场地）：");
        String location = readInput(scanner, "请输入位置（可留空）：");
        String status = readRequiredInput(scanner, "请输入状态（AVAILABLE/OCCUPIED/DISABLED）：");
        displayResult(resourceService.updateResource(user, id, name, type, location, status));
    }

    /** 从控制台读取预约申请字段，时间格式为 yyyy-MM-ddTHH:mm。 */
    private Result<ReserveRecord> submitReservationFromInput(Scanner scanner, User user) {
        String resourceId = readRequiredInput(scanner, "请输入资源 ID：");
        LocalDateTime start = parseDateTime(readRequiredInput(scanner, "请输入开始时间（yyyy-MM-ddTHH:mm）："), "开始时间");
        LocalDateTime end = parseDateTime(readRequiredInput(scanner, "请输入结束时间（yyyy-MM-ddTHH:mm）："), "结束时间");
        String remark = readInput(scanner, "请输入备注（可留空）：");
        return reserveService.submitReservation(user, resourceId, start, end, remark);
    }

    /** 从控制台读取管理员审核字段，按记录编号选择通过或驳回。 */
    private Result<ReserveRecord> auditReservationFromInput(Scanner scanner, User user) {
        String recordId = readRequiredInput(scanner, "请输入预约记录 ID：");
        String decision = readRequiredInput(scanner, "请输入审核结果（PASS/REJECT）：").toUpperCase(Locale.ROOT);
        ReserveStatus status;
        try {
            status = ReserveStatus.valueOf(decision);
        } catch (IllegalArgumentException e) {
            return Result.fail("操作失败：审核结果只能为 PASS 或 REJECT。");
        }
        String remark = readInput(scanner, "请输入审核备注（可留空）：");
        return reserveService.auditReservation(user, recordId, status, remark);
    }

    /** 从控制台读取筛选条件，空值表示不限制该字段。 */
    private Result<List<ReserveRecord>> filterReservationsFromInput(Scanner scanner, User user) {
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
        return reserveService.filterReservations(user, resourceId, applicant, status);
    }

    /** 从控制台读取学生要取消的记录编号。 */
    private Result<ReserveRecord> cancelReservationFromInput(Scanner scanner, User user) {
        String recordId = readRequiredInput(scanner, "请输入要取消的预约记录 ID：");
        return reserveService.cancelReservation(user, recordId);
    }

    /** 读取一行输入；EOF 时由上层结束当前会话。 */
    private String readInput(Scanner scanner, String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println("输入已结束，程序退出。");
            return null;
        }
        return scanner.nextLine();
    }

    /** 读取不可为空的菜单参数，空值转换为统一业务异常。 */
    private String readRequiredInput(Scanner scanner, String prompt) {
        String value = readInput(scanner, prompt);
        if (value == null) {
            throw new BusinessException("输入已结束。");
        }
        if (value.trim().isEmpty()) {
            throw new EmptyParameterException("输入项");
        }
        return value.trim();
    }

    /** 解析控制台时间，统一转换为友好 Result 边界异常。 */
    private LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldName + "格式错误，请使用 yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-ddTHH:mm。");
        }
    }

    /** 只决定菜单展示；权限校验由服务再次独立执行。 */
    private boolean isAdministrator(User user) {
        return user != null && "admin".equals(user.getRole());
    }
}
