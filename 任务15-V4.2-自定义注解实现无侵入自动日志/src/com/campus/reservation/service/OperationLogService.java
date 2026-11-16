package com.campus.reservation.service;

import com.campus.reservation.common.Result;
import com.campus.reservation.entity.OperationLogRecord;
import com.campus.reservation.entity.User;
import java.util.List;

/** 日志查询契约；所有入口在服务层校验管理员身份。 */
public interface OperationLogService {
    /** 查询全部日志，按操作时间倒序。 */
    Result<List<OperationLogRecord>> queryAllLogs(User user);
    /** 按用户名精确匹配，按时间倒序；空用户名不是查询全部的捷径。 */
    Result<List<OperationLogRecord>> queryLogsByUsername(User user, String username);
}
