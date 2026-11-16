package com.campus.reservation.common;

import com.campus.reservation.common.annotation.OperationLog;
import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.entity.OperationLogRecord;
import com.campus.reservation.entity.User;
import com.campus.reservation.common.util.ObjectReflectUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/** 原生反射切面：只负责采集日志，原结果引用、业务异常与执行次数保持不变。 */
public final class LogAspect {
    /** 静态切面无需实例。 */
    private LogAspect() { }

    /** 读取实现方法注解，反射执行一次；无注解方法不产生操作日志。 */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> invoke(Object target, Method method, Object[] arguments, User currentUser)
            throws Throwable {
        Object[] args = arguments == null ? new Object[0] : arguments.clone();
        Method actual = target.getClass().getMethod(method.getName(), method.getParameterTypes());
        OperationLog annotation = actual.getAnnotation(OperationLog.class);
        if (annotation == null) { annotation = method.getAnnotation(OperationLog.class); }
        Result<T> result = null;
        Throwable failure = null;
        LocalDateTime time = LocalDateTime.now();
        try {
            result = (Result<T>) actual.invoke(target, args);
            return result;
        } catch (InvocationTargetException e) {
            failure = e.getCause();
            throw failure;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            failure = e;
            throw e;
        } finally {
            if (annotation != null) {
                recordSafely(actual, args, currentUser, annotation, time, result, failure);
            }
        }
    }

    /** 后置记录必须故障隔离，不用日志失败替换已完成的业务返回或业务异常。 */
    private static void recordSafely(Method method, Object[] args, User user, OperationLog annotation,
            LocalDateTime time, Result<?> result, Throwable failure) {
        try {
            // 空对象检查仅影响日志身份，不改变业务权限和原调用参数。
            boolean emptyUser = ObjectReflectUtil.isAllFieldNull(user);
            String username = emptyUser ? "未登录" : user.getUsername();
            int actor = annotation.usernameParam();
            if (emptyUser && actor >= 0 && actor < args.length && args[actor] instanceof String) {
                username = (String) args[actor];
            }
            if (username == null || username.trim().isEmpty()) { username = "未登录"; }
            Object[] safeArgs = args.clone();
            for (int i = 0; i < safeArgs.length; i++) {
                if (sensitive(annotation, i)) { safeArgs[i] = "[已脱敏]"; }
            }
            String params = ObjectReflectUtil.objectToString(safeArgs, "password");
            String resultInfo = ObjectReflectUtil.objectToString(result, "password");
            String exception = "";
            if (failure != null) {
                StringWriter text = new StringWriter();
                try (PrintWriter writer = new PrintWriter(text)) { failure.printStackTrace(writer); }
                exception = text.toString();
            }
            // 参数、消息和异常堆栈均移除已声明的密码值，避免把密码作为参数、结果或异常落盘。
            OperationLogRecord record = new OperationLogRecord(UUID.randomUUID().toString(),
                    username, time, method.getDeclaringClass().getName() + "." + method.getName(),
                    annotation.value(), redact(params, annotation, args), redact(resultInfo, annotation, args),
                    redact(exception, annotation, args));
            DaoFactory.getOperationLogDao().save(record);
        } catch (RuntimeException e) {
            DaoFactory.getOperationLogDao().reportFailure("操作日志采集失败，原业务结果不受影响。");
        }
    }

    /** 注解控制脱敏，不按具体业务方法名硬编码判断。 */
    private static boolean sensitive(OperationLog annotation, int index) {
        return Arrays.stream(annotation.sensitiveParams()).anyMatch(value -> value == index);
    }

    /** 同一敏感值即使被业务异常或提示引用，也必须替换。 */
    private static String redact(String text, OperationLog annotation, Object[] args) {
        String safe = text;
        for (int index : annotation.sensitiveParams()) {
            if (index >= 0 && index < args.length && args[index] instanceof String && !((String) args[index]).isEmpty()) {
                safe = safe.replace((String) args[index], "[已脱敏]");
            }
        }
        return safe;
    }
}
