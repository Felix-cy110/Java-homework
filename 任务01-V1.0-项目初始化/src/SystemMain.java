import java.util.Scanner;

/** 校园资源预约系统 V1.0：固定账号登录，逻辑集中在主类。 */
public class SystemMain {

    /** 完成一次登录校验，输出结果后结束。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V1.0 ===");

        // 登录结束后自动关闭输入资源。
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("请输入账号：");
            // 先检查输入是否结束，避免读取异常。
            if (!scanner.hasNextLine()) {
                System.out.println("登录失败：未读取到账号。");
                return;
            }
            // 整行读取，保留空格以便严格校验。
            String username = scanner.nextLine();

            System.out.print("请输入密码：");
            if (!scanner.hasNextLine()) {
                System.out.println("登录失败：未读取到密码。");
                return;
            }
            String password = scanner.nextLine();

            // 固定测试账号的账号和密码均须完全匹配。
            if ("admin".equals(username) && "123456".equals(password)) {
                System.out.println("登录成功，欢迎管理员！");
            } else if ("student".equals(username) && "123456".equals(password)) {
                System.out.println("登录成功，欢迎学生！");
            } else {
                System.out.println("登录失败：账号或密码错误。");
            }
        }
    }
}
