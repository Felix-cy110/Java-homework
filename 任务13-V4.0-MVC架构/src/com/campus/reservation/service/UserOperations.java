package com.campus.reservation.service;

import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;

/** 用户业务契约：视图依赖此小接口，具体实现负责校验和数据访问。 */
public interface UserOperations {
    /**
     * 保留账号为空时立即提示的交互顺序，校验仍由业务层执行。
     *
     * @param username 待校验的账号
     */
    Result<Void> checkUsername(String username);

    /** 只补建缺失的管理员，初始化失败以 Result 返回。 */
    Result<Void> initializeDefaultAdministrator();

    /**
     * 校验注册数据并查重，保存成功后更新内存。
     *
     * @param username 注册账号
     * @param password 注册密码
     */
    Result<User> registerUser(String username, String password);

    /**
     * 检查账号密码并返回登录用户。
     *
     * @param username 登录账号
     * @param password 登录密码
     */
    Result<User> loginUser(String username, String password);

    /**
     * 按账号精确查询，使用 Optional 表达不存在状态。
     *
     * @param username 要查询的账号
     */
    Result<User> findUserByUsername(String username);
}
