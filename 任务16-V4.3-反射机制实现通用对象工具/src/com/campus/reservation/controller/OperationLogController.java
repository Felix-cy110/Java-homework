package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.entity.OperationLogRecord;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.OperationLogService;
import com.campus.reservation.service.OperationLogServiceImpl;
import java.util.List;

/** 日志控制器只转发；查询不加操作注解，防止查询日志递归生成日志。 */
public class OperationLogController extends ControllerSupport {
    private final OperationLogService service = OperationLogServiceImpl.getInstance();

    /** 无参装配查询服务，View 不访问 DAO。 */
    public OperationLogController() { }
    /** 所有查询也通过切面；无注解时原样执行但不记日志。 */
    public Result<List<OperationLogRecord>> queryAllLogs(User user) {
        return invoke(service, "queryAllLogs", new Class<?>[] {User.class}, user, user);
    }
    /** 转发精确用户名筛选，权限由服务保证。 */
    public Result<List<OperationLogRecord>> queryLogsByUsername(User user, String username) {
        return invoke(service, "queryLogsByUsername", new Class<?>[] {User.class, String.class}, user, user, username);
    }
}
