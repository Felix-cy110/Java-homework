package com.campus.reservation;

import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import com.campus.reservation.exception.PasswordErrorException;
import com.campus.reservation.exception.UserAlreadyExistsException;
import com.campus.reservation.exception.UserNotFoundException;
import com.campus.reservation.util.FileUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** 根包启动入口：处理 V1.4 控制台交互，协调实体、文件工具与业务异常。 */
public class SystemMain {

    /** 加载历史用户并处理菜单，集中捕获业务与系统异常。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V1.4 ===");
        System.out.println("提示：输入 back 返回上一级菜单；最外层保持当前页面。输入 \\back 可作为普通文本 back。");
        // 所有菜单共用输入资源，程序结束后自动关闭。
        try (Scanner scanner = new Scanner(System.in)) {
            List<User> users = FileUtil.loadUsers();
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
                        User newUser = registerUser(users, username, password);
                        System.out.println("注册成功，请选择登录。");
                        System.out.println("用户信息：" + newUser);
                    } else {
                        User user = loginUser(users, username, password);
                        System.out.println("登录成功，欢迎学生！");
                        System.out.println("用户信息：" + user);
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
            System.err.println("系统错误：用户数据加载失败，请检查 user.txt 的内容、占用或访问权限后重试。");
            System.exit(1);
        } catch (RuntimeException e) {
            // 未预期的系统错误停止运行，避免继续操作不确定的数据。
            System.err.println("系统错误：程序运行异常，请重新启动后重试。");
            System.exit(1);
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

    /** 反斜杠转义保留字，已有账号或字段为 back 时仍可按原值输入。 */
    private static String literalInput(String value) {
        int prefix = 0;
        while (prefix < value.length() && value.charAt(prefix) == '\\') {
            prefix++;
        }
        return prefix > 0 && "back".equals(value.substring(prefix).trim())
                ? value.substring(1) : value;
    }
}
