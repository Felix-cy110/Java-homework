package com.campus.reservation.view;

import com.campus.reservation.controller.UserController;
import com.campus.reservation.controller.ResourceController;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.common.BusinessException;
import java.util.Scanner;

/** 主视图：处理登录注册交互，并根据登录模型选择角色菜单。 */
public class MainView extends ConsoleView {
    private final UserController userController;

    /** 接收控制器；视图不创建服务，不加载数据文件。 */
    public MainView(UserController userController, ResourceController resourceController,
            ReserveController reserveController) {
        super(new Scanner(System.in), resourceController, reserveController);
        this.userController = userController;
    }

    /** 展示版本标题，启动失败也保留当前版本标识。 */
    public static void printBanner() {
        System.out.println("=== 校园资源预约系统 V4.0 ===");
        System.out.println("提示：输入 back 返回上一级菜单；最外层保持当前页面。输入 \\back 可作为普通文本 back。");
    }

    /** 展示入口传入的启动错误，避免输出底层堆栈。 */
    public static void showStartupError(String message) {
        System.err.println(message);
    }

    /** 执行主菜单并关闭输入流，返回与基线一致的退出码。 */
    public int start() {
        try (Scanner input = scanner) {
            Result<Void> initialization = userController.initializeDefaultAdministrator();
            if (!initialization.isSuccess()) {
                showStartupError(initialization.getMsg());
                return 1;
            }
            while (true) {
                String operation = "";
                try {
                    System.out.println();
                    printMenu();
                    System.out.print("请选择：");
                    if (!input.hasNextLine()) {
                        System.out.println("输入已结束，程序退出。");
                        break;
                    }
                    String choice = input.nextLine().trim();
                    // 首页没有上级，返回命令仅重新展示首页。
                    if ("back".equals(choice)) {
                        continue;
                    }
                    if ("0".equals(choice)) {
                        System.out.println("程序已退出。");
                        break;
                    }
                    if (!("1".equals(choice) || "2".equals(choice))) {
                        throw new BusinessException("无效选项，请输入 1、2 或 0。");
                    }
                    boolean registering = "2".equals(choice);
                    operation = registering ? "注册" : "登录";
                    String username = readInput("请输入账号：");
                    if (username == null) {
                        break;
                    }
                    Result<Void> check = userController.checkUsername(username);
                    if (!check.isSuccess()) {
                        displayResult(Result.fail(operation + "失败：" + check.getMsg()));
                        continue;
                    }
                    String password = readInput("请输入密码：");
                    if (password == null) {
                        break;
                    }
                    if (registering) {
                        displayResult(userController.registerUser(username, password));
                    } else {
                        Result<User> login = userController.loginUser(username, password);
                        displayResult(login);
                        if (login.isSuccess() && !openRoleMenu(login.getData())) {
                            break;
                        }
                    }
                } catch (BackNavigation e) {
                    // 取消登录或注册，尚未调用写入业务，继续首页循环。
                    continue;
                } catch (BusinessException e) {
                    String prefix = operation.isEmpty() ? "" : operation + "失败：";
                    displayResult(Result.fail(prefix + e.getMessage()));
                }
            }
            return 0;
        }
    }

    /** 角色仅用于视图路由，实际权限仍在服务层独立校验。 */
    private boolean openRoleMenu(User user) {
        ConsoleView view = "admin".equals(user.getRole())
                ? new AdminView(scanner, resourceController, reserveController)
                : new StudentView(scanner, resourceController, reserveController);
        return view.start(user);
    }

    /** 展示主菜单，保留选项编号。 */
    @Override
    protected void printMenu() {
        System.out.println("1.登录  2.注册  0.退出");
    }

    /** 主菜单由无参 start 处理，不接收已登录菜单请求。 */
    @Override
    protected boolean handleChoice(String choice, User user) {
        return false;
    }
}
