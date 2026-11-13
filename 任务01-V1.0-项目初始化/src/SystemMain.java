import java.util.Scanner;

/** 校园资源预约系统 V1.0：固定账号登录，逻辑集中在主类。 */
public class SystemMain {

    /** 支持取消当前登录并回到起点；完成一次校验后结束。 */
    public static void main(String[] args) {
        System.out.println("=== 校园资源预约系统 V1.0 ===");
        System.out.println("提示：输入 back 返回上一级菜单；最外层保持当前页面。输入 \\back 可作为普通文本 back。");

        // 登录结束后自动关闭输入资源。
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("请输入账号：");
                // 先检查输入是否结束，避免读取异常。
                if (!scanner.hasNextLine()) {
                    System.out.println("登录失败：未读取到账号。");
                    return;
                }
                // 整行读取，保留空格以便严格校验。
                String username = scanner.nextLine();
                // 返回登录起点，不将命令当作凭据校验。
                if ("back".equals(username.trim())) {
                    continue;
                }
                username = literalInput(username);

                System.out.print("请输入密码：");
                if (!scanner.hasNextLine()) {
                    System.out.println("登录失败：未读取到密码。");
                    return;
                }
                String password = scanner.nextLine();
                // 返回登录起点，不将命令当作凭据校验。
                if ("back".equals(password.trim())) {
                    continue;
                }
                password = literalInput(password);

                // 固定测试账号的账号和密码均须完全匹配。
                if ("admin".equals(username) && "123456".equals(password)) {
                    System.out.println("登录成功，欢迎管理员！");
                } else if ("student".equals(username) && "123456".equals(password)) {
                    System.out.println("登录成功，欢迎学生！");
                } else {
                    System.out.println("登录失败：账号或密码错误。");
                }
                break;
            }
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
