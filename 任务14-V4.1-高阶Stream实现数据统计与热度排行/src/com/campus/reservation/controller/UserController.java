package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.UserOperations;
import com.campus.reservation.service.UserService;
import java.io.IOException;

/** 用户控制器：接收视图参数，转发账号校验、登录与注册请求。 */
public class UserController extends ControllerSupport {
    private final UserOperations userService;

    /** 获取已验收的服务单例，不创建新的业务实例。 */
    public UserController() throws IOException {
        userService = UserService.getInstance();
    }

    /** 控制器检查账号非空，保留输入密码前提示失败的顺序。 */
    public Result<Void> checkUsername(String username) {
        return execute(() -> {
            requireNotEmpty(username, "账号");
            return userService.checkUsername(username);
        });
    }

    /** 通过业务层补建默认管理员，控制器不访问用户文件。 */
    public Result<Void> initializeDefaultAdministrator() {
        return execute(() -> userService.initializeDefaultAdministrator());
    }

    /** 转发注册，角色和重复账号规则统一由业务层判断。 */
    public Result<User> registerUser(String username, String password) {
        return execute(() -> userService.registerUser(username, password));
    }

    /** 转发登录并返回用户模型，不处理菜单展示。 */
    public Result<User> loginUser(String username, String password) {
        return execute(() -> userService.loginUser(username, password));
    }

    /** 转发精确账号查询，保留原有业务接口。 */
    public Result<User> findUserByUsername(String username) {
        return execute(() -> userService.findUserByUsername(username));
    }
}
