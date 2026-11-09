package com.campus.reservation.entity;

import java.time.LocalDateTime;

/** 用户实体：封装账号、密码、角色与注册时间，供入口与文件工具共同使用。 */
public class User {
    private String username;
    private String password;
    private String role; // 角色值：admin 或 student。
    private LocalDateTime registerTime;

    /** 创建空对象，字段由 setter 填充。 */
    public User() {
    }

    /** 使用调用方提供的数据初始化全部字段。 */
    public User(String username, String password, String role, LocalDateTime registerTime) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.registerTime = registerTime;
    }

    // 通过访问方法读写私有字段，校验规则由主类负责。
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(LocalDateTime registerTime) {
        this.registerTime = registerTime;
    }

    /** 展示全部字段标识，密码使用固定掩码避免直接打印原值。 */
    @Override
    public String toString() {
        return "User{username='" + username + "', password='******', role='" + role
                + "', registerTime=" + registerTime + "}";
    }
}
