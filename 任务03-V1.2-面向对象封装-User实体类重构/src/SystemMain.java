import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** 校园资源预约系统 V1.2：使用用户实体保存注册与登录数据。 */
public class SystemMain {

    /** 循环处理登录和注册，退出后清空本次运行的账号数据。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V1.2 ===");
        // 从空集合开始，只有本次运行中注册的账号可以登录。
        List<User> users = new ArrayList<>();

        // 所有菜单共用输入资源，程序结束后自动关闭。
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
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
                    System.out.println("无效选项，请输入 1、2 或 0。");
                    continue;
                }

                boolean registering = "2".equals(choice);
                String operation = registering ? "注册" : "登录";
                System.out.print("请输入账号：");
                // 先检查输入是否结束，避免读取异常。
                if (!scanner.hasNextLine()) {
                    System.out.println(operation + "失败：未读取到账号，程序退出。");
                    break;
                }
                // 整行读取，保留账号和密码中的空格用于精确匹配。
                String username = scanner.nextLine();
                if (!isValidUsername(username)) {
                    System.out.println(operation + "失败：账号不能为空。");
                    continue;
                }

                System.out.print("请输入密码：");
                if (!scanner.hasNextLine()) {
                    System.out.println(operation + "失败：未读取到密码，程序退出。");
                    break;
                }
                String password = scanner.nextLine();
                User existingUser = findUserByUsername(users, username);

                if (registering) {
                    if (!isValidPassword(password)) {
                        System.out.println("注册失败：密码不能为空且长度至少为 6 位。");
                        continue;
                    }
                    // 插入前查重，避免覆盖已注册账号的密码。
                    if (existingUser != null) {
                        System.out.println("注册失败：账号已存在。");
                        continue;
                    }
                    // 注册成功时确定角色和时间，实体本身不生成业务数据。
                    User newUser = new User(username, password, "student", LocalDateTime.now());
                    users.add(newUser);
                    System.out.println("注册成功，请选择登录。");
                    System.out.println("用户信息：" + newUser);
                } else if (existingUser != null && password.equals(existingUser.getPassword())) {
                    System.out.println("登录成功，欢迎学生！");
                    System.out.println("用户信息：" + existingUser);
                } else {
                    System.out.println("登录失败：账号或密码错误。");
                }
            }
        }
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

    /** 校验账号非空，拒绝空字符串及仅含常规空白的输入。 */
    private static boolean isValidUsername(String username) {
        return username != null && !username.trim().isEmpty();
    }

    /** 校验注册密码非空，且原始输入长度至少为 6。 */
    private static boolean isValidPassword(String password) {
        return password != null && !password.trim().isEmpty() && password.length() >= 6;
    }
}
