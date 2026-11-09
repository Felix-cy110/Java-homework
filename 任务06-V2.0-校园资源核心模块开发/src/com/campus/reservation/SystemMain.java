package com.campus.reservation;

import com.campus.reservation.entity.CampusResource;
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

/** V2.0 控制台入口：继承用户业务，按登录角色处理资源菜单与业务校验。 */
public class SystemMain {
    // 按用户要求提供课程默认管理员，仅在账号不存在时用于初始化。
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    /** 加载历史数据并初始化缺失的管理员，集中处理登录注册和系统错误。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V2.0 ===");
        String loadingFile = "user.txt";
        // 所有菜单共用输入资源，程序结束后自动关闭。
        try (Scanner scanner = new Scanner(System.in)) {
            List<User> users = FileUtil.loadUsers();
            loadingFile = "resource.txt";
            List<CampusResource> resources = FileUtil.loadResources();
            loadingFile = "reserve.txt";
            // 启动时验证关联文件；实际删除前仍重读，避免漏过新增关联。
            FileUtil.loadReservedResourceIds();
            try {
                initializeDefaultAdministrator(users);
            } catch (IOException e) {
                // 管理员未成功落盘时停止启动，不保留仅在内存中有效的账号。
                System.err.println("系统错误：默认管理员初始化失败，请检查 user.txt 的占用或访问权限后重试。");
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
                    validateUsername(username);

                    System.out.print("请输入密码：");
                    if (!scanner.hasNextLine()) {
                        System.out.println(operation + "失败：未读取到密码，程序退出。");
                        break;
                    }
                    // 保留整行输入中的空格，业务方法只校验，不修改原值。
                    String password = scanner.nextLine();
                    if (registering) {
                        User newUser = registerUser(users, username, password);
                        System.out.println("注册成功，请选择登录。");
                        System.out.println("用户信息：" + newUser);
                    } else {
                        User user = loginUser(users, username, password);
                        String roleName = "admin".equals(user.getRole()) ? "管理员" : "学生";
                        System.out.println("登录成功，欢迎" + roleName + "！");
                        System.out.println("用户信息：" + user);
                        if (!runResourceMenu(scanner, user, resources)) {
                            break;
                        }
                    }
                } catch (BusinessException e) {
                    // 统一按父类型处理，新增业务子异常无需增加捕获分支。
                    String prefix = operation.isEmpty() ? "" : operation + "失败：";
                    System.out.println(prefix + e.getMessage());
                } catch (IOException e) {
                    // 保存失败可重试，不向用户暴露底层异常信息。
                    System.out.println("系统错误：用户数据保存失败，账号未创建。请检查文件占用或访问权限后重试。");
                }
            }
        } catch (IOException e) {
            // 加载失败立即停止，避免将历史数据当作空集合覆盖。
            System.err.println("系统错误：数据加载失败，请检查 " + loadingFile
                    + " 的内容、占用或访问权限后重试。");
            System.exit(1);
        } catch (BusinessException e) {
            // 同名普通账号属于可解释的初始化冲突，保留原数据并友好提示。
            System.err.println("启动失败：" + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // 未预期的系统错误停止运行，避免继续操作不确定的数据。
            System.err.println("系统错误：程序运行异常，请重新启动后重试。");
            System.exit(1);
        }
    }

    /** 仅补建缺失的默认管理员；已有管理员不重置，普通同名账号不提升权限。 */
    private static void initializeDefaultAdministrator(List<User> users) throws IOException {
        User existing = findUserByUsername(users, DEFAULT_ADMIN_USERNAME);
        if (existing != null) {
            if (!"admin".equals(existing.getRole())) {
                throw new BusinessException("默认管理员账号 admin 已被非管理员占用，请先处理 user.txt 中的同名账号；原账号未被覆盖。");
            }
            return;
        }
        User administrator = new User(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD,
                "admin", LocalDateTime.now());
        List<User> updatedUsers = new ArrayList<>(users);
        updatedUsers.add(administrator);
        // 沿用注册的保存顺序，成功落盘后才改变内存，保留已有学生账号。
        FileUtil.saveUsers(updatedUsers);
        users.add(administrator);
    }

    /** 登录后按角色显示菜单；返回 false 表示 EOF，true 表示正常退出登录。 */
    private static boolean runResourceMenu(Scanner scanner, User user, List<CampusResource> resources) {
        while (true) {
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
                choice = choice.trim();
                if ("0".equals(choice)) {
                    System.out.println("已退出登录。");
                    return true;
                }
                if ("2".equals(choice)) {
                    List<CampusResource> visible = queryResources(user, resources);
                    System.out.println("admin".equals(user.getRole()) ? "全部资源：" : "可预约资源：");
                    if (visible.isEmpty()) {
                        System.out.println("暂无符合条件的资源。");
                    }
                    for (CampusResource resource : visible) {
                        System.out.println(resource);
                    }
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
                    findResourceById(resources, id);
                }
                if ("4".equals(choice)) {
                    deleteResource(user, resources, id);
                    System.out.println("资源删除成功。");
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
                CampusResource resource;
                if ("1".equals(choice)) {
                    resource = addResource(user, resources, name, type, location);
                    System.out.println("资源新增成功：" + resource);
                } else {
                    String status = readResourceInput(scanner, "请输入状态（AVAILABLE/OCCUPIED/DISABLED）：");
                    if (status == null) {
                        return false;
                    }
                    resource = updateResource(user, resources, id, name, type, location, status);
                    System.out.println("资源修改成功：" + resource);
                }
            } catch (BusinessException e) {
                System.out.println("资源操作失败：" + e.getMessage());
            } catch (IOException e) {
                // 候选数据未提交到内存，文件错误后可在当前菜单重试。
                System.out.println("系统错误：资源操作未完成。请检查 resource.txt、reserve.txt 的内容、占用或访问权限后重试。");
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
        return scanner.nextLine();
    }

    /** 管理员新增资源，生成唯一 ID，文件保存成功后更新内存。 */
    private static CampusResource addResource(User user, List<CampusResource> resources,
                                             String name, String type, String location) throws IOException {
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
        return resource;
    }

    /** 管理员查看全部，学生只返回可预约资源，其他身份拒绝查询。 */
    private static List<CampusResource> queryResources(User user, List<CampusResource> resources) {
        if (user == null || !("admin".equals(user.getRole()) || "student".equals(user.getRole()))) {
            throw new PermissionDeniedException("请使用有效账号登录后查询资源。");
        }
        List<CampusResource> visible = new ArrayList<>();
        for (CampusResource resource : resources) {
            if ("admin".equals(user.getRole()) || "AVAILABLE".equals(resource.getStatus())) {
                visible.add(resource);
            }
        }
        return visible;
    }

    /** 修改时构造新实体，保存失败不改变原对象，成功后替换内存中的对象。 */
    private static CampusResource updateResource(User user, List<CampusResource> resources,
                                                String id, String name, String type, String location,
                                                String status) throws IOException {
        requireAdministrator(user);
        CampusResource existing = findResourceById(resources, id);
        validateResourceFields(name, type, location, status);
        CampusResource changed = new CampusResource(existing.getId(), name, type, status, location);
        int index = resources.indexOf(existing);
        List<CampusResource> updated = new ArrayList<>(resources);
        updated.set(index, changed);
        FileUtil.saveResources(updated);
        resources.set(index, changed);
        return changed;
    }

    /** 删除前检查实际预约关联；关联读取或保存失败均不改变资源列表。 */
    private static void deleteResource(User user, List<CampusResource> resources, String id) throws IOException {
        requireAdministrator(user);
        CampusResource existing = findResourceById(resources, id);
        if (FileUtil.loadReservedResourceIds().contains(existing.getId())) {
            throw new ResourceReservedException();
        }
        List<CampusResource> updated = new ArrayList<>(resources);
        updated.remove(existing);
        FileUtil.saveResources(updated);
        resources.remove(existing);
    }

    /** 按 ID 精确查找；空 ID 或不存在的 ID 均通过业务异常报告。 */
    private static CampusResource findResourceById(List<CampusResource> resources, String id) {
        validateNotEmpty(id, "资源 ID");
        for (CampusResource resource : resources) {
            if (id.equals(resource.getId())) {
                return resource;
            }
        }
        throw new ResourceNotFoundException();
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

    /** 校验注册数据并查重，文件保存成功后才更新内存集合。 */
    private static User registerUser(List<User> users, String username, String password) throws IOException {
        validateUsername(username);
        validatePassword(password);
        if (findUserByUsername(users, username) != null) {
            throw new UserAlreadyExistsException();
        }
        User newUser = new User(username, password, "student", LocalDateTime.now());
        List<User> updatedUsers = new ArrayList<>(users);
        updatedUsers.add(newUser);
        FileUtil.saveUsers(updatedUsers);
        users.add(newUser);
        return newUser;
    }

    /** 分别检查用户存在和密码匹配，成功时返回原用户对象。 */
    private static User loginUser(List<User> users, String username, String password) {
        validateUsername(username);
        validateNotEmpty(password, "密码");
        User user = findUserByUsername(users, username);
        if (user == null) {
            throw new UserNotFoundException();
        }
        if (!password.equals(user.getPassword())) {
            throw new PasswordErrorException();
        }
        return user;
    }

    /** 按账号精确查找用户，未找到时返回 null。 */
    private static User findUserByUsername(List<User> users, String username) {
        for (User user : users) {
            if (username.equals(user.getUsername())) {
                return user;
            }
        }
        return null;
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
