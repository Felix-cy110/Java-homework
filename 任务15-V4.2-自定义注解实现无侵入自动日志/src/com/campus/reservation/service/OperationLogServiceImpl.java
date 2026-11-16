package com.campus.reservation.service;

import com.campus.reservation.common.BusinessException;
import com.campus.reservation.common.Result;
import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.dao.OperationLogDao;
import com.campus.reservation.entity.OperationLogRecord;
import com.campus.reservation.entity.User;
import com.campus.reservation.exception.EmptyParameterException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 日志查询服务单例：权限、精确筛选及倒序排序，不负责日志采集。 */
public final class OperationLogServiceImpl implements OperationLogService {
    private static final OperationLogServiceImpl INSTANCE = new OperationLogServiceImpl();
    private final OperationLogDao dao = DaoFactory.getOperationLogDao();

    /** 使用工厂中的日志 DAO，避免重复加载文件。 */
    private OperationLogServiceImpl() { }
    /** 获取唯一查询服务。 */
    public static OperationLogServiceImpl getInstance() { return INSTANCE; }

    /** 查询全部日志；拒绝学生、未登录或无效角色。 */
    @Override
    public Result<List<OperationLogRecord>> queryAllLogs(User user) { return query(user, null, false); }

    /** 用户名采用精确匹配，不擅自 trim 字面账号。 */
    @Override
    public Result<List<OperationLogRecord>> queryLogsByUsername(User user, String username) {
        return query(user, username, true);
    }

    /** 先鉴权再检查日志状态，避免向未授权用户泄露日志故障和内容。 */
    private Result<List<OperationLogRecord>> query(User user, String username, boolean filter) {
        try {
            BusinessRules.requireAdministrator(user);
            if (filter && !Optional.ofNullable(username).filter(value -> !value.trim().isEmpty()).isPresent()) {
                throw new EmptyParameterException("用户名");
            }
            Result<List<OperationLogRecord>> loaded = dao.findAll();
            if (!loaded.isSuccess()) { return Result.fail(loaded.getMsg()); }
            List<OperationLogRecord> rows = loaded.getData().stream()
                    .filter(row -> !filter || Objects.equals(username, row.getUsername()))
                    .sorted(Comparator.comparing(OperationLogRecord::getOperateTime).reversed()
                            .thenComparing(OperationLogRecord::getLogId))
                    .collect(Collectors.toList());
            return Result.success("操作日志（时间倒序）：", rows);
        } catch (BusinessException e) { return Result.fail(e.getMessage()); }
    }
}
