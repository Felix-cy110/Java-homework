package com.campus.reservation.service;

import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.BusinessException;
import com.campus.reservation.exception.PasswordErrorException;
import com.campus.reservation.exception.UserAlreadyExistsException;
import com.campus.reservation.exception.UserNotFoundException;
import com.campus.reservation.dao.UserDao;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import static com.campus.reservation.service.BusinessRules.isAdministrator;
import static com.campus.reservation.service.BusinessRules.validateUsername;
import static com.campus.reservation.service.BusinessRules.validatePassword;
import static com.campus.reservation.service.BusinessRules.validateNotEmpty;

/** 用户业务：默认管理员、注册和登录校验，持久化委托 UserDao。 */
public class UserService implements UserOperations {
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";
    private final UserDao userDao;

    /** 注入当前应用的用户数据访问对象。 */
    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    /** 保留账号为空时立即提示的交互顺序，校验仍由业务层执行。 */
    public Result<Void> checkUsername(String username) {
        try {
            validateUsername(username);
            return Result.success("账号格式正确。");
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 只补建缺失的管理员，初始化失败以 Result 返回。 */
    public Result<Void> initializeDefaultAdministrator() {
        try {
            Result<User> found = findUserByUsername(DEFAULT_ADMIN_USERNAME);
            if (found.isSuccess()) {
                if (!"admin".equals(found.getData().getRole())) {
                    throw new BusinessException("默认管理员账号 admin 已被非管理员占用，请先处理 user.txt 中的同名账号；原账号未被覆盖。");
                }
                return Result.success("默认管理员已存在。");
            }
            User administrator = new User(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD,
                    "admin", LocalDateTime.now());
            userDao.insert(administrator);
            return Result.success("默认管理员初始化成功。");
        } catch (BusinessException e) {
            return Result.fail("启动失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：默认管理员初始化失败，请检查 user.txt 的占用或访问权限后重试。");
        }
    }

    /** 校验注册数据并查重，保存成功后更新内存。 */
    public Result<User> registerUser(String username, String password) {
        try {
            validateUsername(username);
            validatePassword(password);
            if (findUserByUsername(username).isSuccess()) {
                throw new UserAlreadyExistsException();
            }
            User newUser = new User(username, password, "student", LocalDateTime.now());
            userDao.insert(newUser);
            return Result.success("注册成功，请选择登录。", newUser);
        } catch (BusinessException e) {
            return Result.fail("注册失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：用户数据保存失败，账号未创建。");
        }
    }

    /** 检查账号密码并返回登录用户。 */
    public Result<User> loginUser(String username, String password) {
        try {
            validateUsername(username);
            validateNotEmpty(password, "密码");
            Result<User> found = findUserByUsername(username);
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
    public Result<User> findUserByUsername(String username) {
        try {
            validateUsername(username);
            Optional<User> found = userDao.findByUsername(username);
            if (found.isPresent()) {
                return Result.success("用户查询成功。", found.get());
            }
            throw new UserNotFoundException();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }
}
