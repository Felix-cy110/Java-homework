package com.campus.reservation;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.exception.PasswordErrorException;
import com.campus.reservation.exception.PermissionDeniedException;
import com.campus.reservation.exception.ResourceNotFoundException;
import com.campus.reservation.exception.ResourceReservedException;
import com.campus.reservation.exception.UserAlreadyExistsException;
import com.campus.reservation.exception.UserNotFoundException;
import com.campus.reservation.util.FileUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/** V2.1 控制台入口：继承原业务，通过 Result 统一传递成功、失败与返回数据。 */
public class SystemMain {
    // 按用户要求提供课程默认管理员，仅在账号不存在时用于初始化。
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    /** 加载历史数据并初始化缺失的管理员，集中处理登录注册和系统错误。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V2.1 ===");
        System.out.println("提示：输入 back 返回上一级菜单；最外层保持当前页面。输入 \\back 可作为普通文本 back。");
        String loadingFile = "user.txt";
        // 所有菜单共用输入资源，程序结束后自动关闭。
        try (Scanner scanner = new Scanner(System.in)) {
            List<User> users = FileUtil.loadUsers();
            loadingFile = "resource.txt";
            List<CampusResource> resources = FileUtil.loadResources();
            loadingFile = "reserve.txt";
            // 启动时验证关联文件；实际删除前仍重读，避免漏过新增关联。
            FileUtil.loadReservedResourceIds();
            Result<Void> initialization = initializeDefaultAdministrator(users);
            if (!initialization.isSuccess()) {
                // 初始化失败仍停止启动，避免使用尚未成功落盘的账号。
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
                    // 首页没有上级，返回命令仅重新展示首页。
                    if ("back".equals(choice)) {
                        continue;
                    }
                    if ("0".equals(choice)) {
                        System.out.println("程序已退出。");
                        break;
                    }
                    if (!"1".equals(choice) && !"2".equals(choice)) {
                        throw new BusinessException("无效选项，请输入 1、2 或 0。");
                    }

                    boolean registering = "2".equals(choice);
                    operation = registering ? "注册" : "登录";
                    System.out.print("请输入账号：");
                    // EOF 表示输入结束，沿用原有退出行为。
                    if (!scanner.hasNextLine()) {
                        System.out.println(operation + "失败：未读取到账号，程序退出。");
                        break;
                    }
                    String username = scanner.nextLine();
                    // 未提交的输入直接丢弃，回到登录注册首页。
                    if ("back".equals(username.trim())) {
                        continue;
                    }
                    username = literalInput(username);
                    validateUsername(username);

                    System.out.print("请输入密码：");
                    if (!scanner.hasNextLine()) {
                        System.out.println(operation + "失败：未读取到密码，程序退出。");
                        break;
                    }
                    // 保留整行输入中的空格，业务方法只校验，不修改原值。
                    String password = scanner.nextLine();
                    // 未提交的输入直接丢弃，回到登录注册首页。
                    if ("back".equals(password.trim())) {
                        continue;
                    }
                    password = literalInput(password);
                    if (registering) {
                        displayResult(registerUser(users, username, password));
                    } else {
                        Result<User> result = loginUser(users, username, password);
                        displayResult(result);
                        // 仅使用成功结果的数据建立登录会话。
                        if (result.isSuccess() && !runResourceMenu(scanner, result.getData(), resources)) {
                            break;
                        }
                    }
                } catch (BusinessException e) {
                    // 输入阶段的业务异常也转为 Result，复用同一展示入口。
                    String prefix = operation.isEmpty() ? "" : operation + "失败：";
                    displayResult(Result.fail(prefix + e.getMessage()));
                }
            }
        } catch (IOException e) {
            // 加载失败立即停止，避免将历史数据当作空集合覆盖。
            Result<Void> failure = Result.fail("系统错误：数据加载失败，请检查 " + loadingFile
                    + " 的内容、占用或访问权限后重试。");
            System.err.println(failure.getMsg());
            System.exit(1);
        } catch (RuntimeException e) {
            // 未预期的系统错误停止运行，避免继续操作不确定的数据。
            Result<Void> failure = Result.fail("系统错误：程序运行异常，请重新启动后重试。");
            System.err.println(failure.getMsg());
            System.exit(1);
        }
    }

    /** 只补建缺失的管理员，初始化失败以 Result 返回并由启动入口停止程序。 */
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
            // 沿用先保存后更新内存的顺序，不覆盖已有用户。
            FileUtil.saveUsers(updatedUsers);
            users.add(administrator);
            return Result.success("默认管理员初始化成功。");
        } catch (BusinessException e) {
            return Result.fail("启动失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：默认管理员初始化失败，请检查 user.txt 的占用或访问权限后重试。");
        }
    }

    /** 统一展示 Result，失败只显示消息，成功时按返回数据的形态输出。 */
    private static void displayResult(Result<?> result) {
        System.out.println(result.getMsg());
        if (!result.isSuccess()) {
            return;
        }
        Object data = result.getData();
        if (data instanceof List<?>) {
            List<?> items = (List<?>) data;
            if (items.isEmpty()) {
                System.out.println("暂无符合条件的资源。");
            }
            for (Object item : items) {
                System.out.println(item);
            }
        } else if (data instanceof User) {
            System.out.println("用户信息：" + data);
        } else if (data != null) {
            System.out.println(data);
        }
    }

    /** 登录后按角色显示菜单；返回 false 表示 EOF，true 表示正常退出登录。 */
    private static boolean runResourceMenu(Scanner scanner, User user, List<CampusResource> resources) {
        while (true) {
            boolean choosingMenu = true;
            try {
                System.out.println();
                if ("admin".equals(user.getRole())) {
                    System.out.println("管理员资源菜单：1.新增  2.查询全部  3.修改  4.删除  0.退出登录");
                } else {
                    System.out.println("学生资源菜单：2.查询可预约资源  0.退出登录");
                }
                String choice = readResourceInput(scanner, "请选择：");
                if (choice == null) {
                    return false;
                }
                choosingMenu = false;
                choice = choice.trim();
                if ("0".equals(choice)) {
                    System.out.println("已退出登录。");
                    return true;
                }
                if ("2".equals(choice)) {
                    displayResult(queryResources(user, resources));
                    continue;
                }
                if (!"1".equals(choice) && !"3".equals(choice) && !"4".equals(choice)) {
                    throw new BusinessException("无效资源菜单选项，请按菜单输入。");
                }
                // 菜单提前拒绝越权；各业务方法也独立校验，不能绕过菜单调用。
                requireAdministrator(user);
                String id = null;
                if (!"1".equals(choice)) {
                    id = readResourceInput(scanner, "请输入资源 ID：");
                    if (id == null) {
                        return false;
                    }
                    id = id.trim();
                    Result<CampusResource> found = findResourceById(resources, id);
                    if (!found.isSuccess()) {
                        displayResult(Result.fail("资源操作失败：" + found.getMsg()));
                        continue;
                    }
                }
                if ("4".equals(choice)) {
                    displayResult(deleteResource(user, resources, id));
                    continue;
                }
                String name = readResourceInput(scanner, "请输入资源名称：");
                if (name == null) {
                    return false;
                }
                validateNotEmpty(name, "资源名称");
                String type = readResourceInput(scanner, "请输入资源类型（教室/实验室/设备/场地）：");
                if (type == null) {
                    return false;
                }
                validateResourceType(type);
                String location = readResourceInput(scanner, "请输入位置（可留空）：");
                if (location == null) {
                    return false;
                }
                Result<CampusResource> result;
                if ("1".equals(choice)) {
                    result = addResource(user, resources, name, type, location);
                } else {
                    String status = readResourceInput(scanner, "请输入状态（AVAILABLE/OCCUPIED/DISABLED）：");
                    if (status == null) {
                        return false;
                    }
                    result = updateResource(user, resources, id, name, type, location, status);
                }
                displayResult(result);
            } catch (BackNavigation e) {
                // 菜单返回首页；表单取消只回当前角色菜单，不提交半成品。
                if (choosingMenu) {
                    System.out.println("已退出登录。");
                    return true;
                }
            } catch (BusinessException e) {
                displayResult(Result.fail("资源操作失败：" + e.getMessage()));
            }
        }
    }

    /** 读取整行资源输入；EOF 时统一提示，不继续执行未填完的操作。 */
    private static String readResourceInput(Scanner scanner, String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println("输入已结束，程序退出。");
            return null;
        }
        String value = scanner.nextLine();
        // 所有字段在解析类型和调用业务前识别返回，包括可空字段。
        if ("back".equals(value.trim())) {
            throw new BackNavigation();
        }
        return literalInput(value);
    }

    /** 新增资源并返回结果；文件保存成功后才更新内存，失败不遗留资源。 */
    private static Result<CampusResource> addResource(User user, List<CampusResource> resources,
                                                     String name, String type, String location) {
        try {
            requireAdministrator(user);
            validateResourceFields(name, type, location, "AVAILABLE");
            String id;
            boolean exists;
            do {
                id = UUID.randomUUID().toString();
                exists = false;
                for (CampusResource resource : resources) {
                    if (id.equals(resource.getId())) {
                        exists = true;
                        break;
                    }
                }
            } while (exists);
            CampusResource resource = new CampusResource(id, name, type, "AVAILABLE", location);
            List<CampusResource> updated = new ArrayList<>(resources);
            updated.add(resource);
            FileUtil.saveResources(updated);
            resources.add(resource);
            return Result.success("资源新增成功：", resource);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成。请检查 resource.txt、reserve.txt 的内容、占用或访问权限后重试。");
        }
    }

    /** 返回按角色过滤的列表结果，学生只包含可预约资源，非法身份返回失败。 */
    private static Result<List<CampusResource>> queryResources(User user, List<CampusResource> resources) {
        try {
            if (user == null || !("admin".equals(user.getRole()) || "student".equals(user.getRole()))) {
                throw new PermissionDeniedException("请使用有效账号登录后查询资源。");
            }
            List<CampusResource> visible = new ArrayList<>();
            for (CampusResource resource : resources) {
                if ("admin".equals(user.getRole()) || "AVAILABLE".equals(resource.getStatus())) {
                    visible.add(resource);
                }
            }
            return Result.success("admin".equals(user.getRole()) ? "全部资源：" : "可预约资源：", visible);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        }
    }

    /** 修改成功结果携带新实体；先保存再替换，失败结果不改变原对象。 */
    private static Result<CampusResource> updateResource(User user, List<CampusResource> resources,
                                                        String id, String name, String type, String location,
                                                        String status) {
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
            return Result.fail("系统错误：资源操作未完成。请检查 resource.txt、reserve.txt 的内容、占用或访问权限后重试。");
        }
    }

    /** 删除前检查预约关联；成功返回无数据结果，失败保留资源列表。 */
    private static Result<Void> deleteResource(User user, List<CampusResource> resources, String id) {
        try {
            requireAdministrator(user);
            Result<CampusResource> found = findResourceById(resources, id);
            if (!found.isSuccess()) {
                return Result.fail("资源操作失败：" + found.getMsg());
            }
            CampusResource existing = found.getData();
            if (FileUtil.loadReservedResourceIds().contains(existing.getId())) {
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
            return Result.fail("系统错误：资源操作未完成。请检查 resource.txt、reserve.txt 的内容、占用或访问权限后重试。");
        }
    }

    /** 按 ID 查询，将空 ID 或不存在的 ID 对应的业务异常转为失败结果。 */
    private static Result<CampusResource> findResourceById(List<CampusResource> resources, String id) {
        try {
            validateNotEmpty(id, "资源 ID");
            for (CampusResource resource : resources) {
                if (id.equals(resource.getId())) {
                    return Result.success("资源查询成功。", resource);
                }
            }
            throw new ResourceNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 所有写操作先校验管理员身份，再检查参数和资源是否存在。 */
    private static void requireAdministrator(User user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new PermissionDeniedException("只有管理员可以新增、修改或删除资源。");
        }
    }

    /** 校验资源必填项与允许的状态；位置可为空字符串但不能为 null。 */
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

    /** 资源类型采用任务书列出的四种值，非法值统一抛出业务异常。 */
    private static void validateResourceType(String type) {
        validateNotEmpty(type, "资源类型");
        if (!("教室".equals(type) || "实验室".equals(type) || "设备".equals(type) || "场地".equals(type))) {
            throw new BusinessException("资源类型只能为教室、实验室、设备或场地。");
        }
    }

    /** 校验注册数据并查重，保存成功后更新内存，再返回用户结果。 */
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
            return Result.fail("系统错误：用户数据保存失败，账号未创建。请检查文件占用或访问权限后重试。");
        }
    }

    /** 检查用户与密码，成功结果携带原用户，失败结果只包含中文消息。 */
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
            String roleName = "admin".equals(user.getRole()) ? "管理员" : "学生";
            return Result.success("登录成功，欢迎" + roleName + "！", user);
        } catch (BusinessException e) {
            return Result.fail("登录失败：" + e.getMessage());
        }
    }

    /** 按账号精确查询，未找到或参数无效时返回失败结果。 */
    private static Result<User> findUserByUsername(List<User> users, String username) {
        try {
            validateUsername(username);
            for (User user : users) {
                if (username.equals(user.getUsername())) {
                    return Result.success("用户查询成功。", user);
                }
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

    /** 反斜杠转义保留字，已有账号或字段为 back 时仍可按原值输入。 */
    private static String literalInput(String value) {
        int prefix = 0;
        while (prefix < value.length() && value.charAt(prefix) == '\\') {
            prefix++;
        }
        return prefix > 0 && "back".equals(value.substring(prefix).trim())
                ? value.substring(1) : value;
    }

    /** 仅在输入与菜单之间传递返回请求，不代表业务失败，也不进入业务层。 */
    private static final class BackNavigation extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
