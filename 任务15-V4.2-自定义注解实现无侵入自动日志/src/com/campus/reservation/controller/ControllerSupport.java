package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.LogAspect;
import com.campus.reservation.entity.User;
import com.campus.reservation.common.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import java.util.function.Supplier;

/** 控制器公共边界：把业务异常转换为视图可展示的统一结果。 */
abstract class ControllerSupport {
    /** 入口只校验必填文本，角色、密码规则等仍由服务判断。 */
    protected void requireNotEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new EmptyParameterException(fieldName);
        }
    }

    /** 只转发请求和包装业务异常，不吸收程序错误或存储故障。 */
    protected <T> Result<T> execute(Supplier<Result<T>> action) {
        try {
            return action.get();
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 按明确签名定位方法，经统一切面调用；保留原业务异常转换边界。 */
    protected <T> Result<T> invoke(Object service, String name, Class<?>[] types, User user, Object... args) {
        return execute(() -> {
            try {
                return LogAspect.invoke(service, service.getClass().getMethod(name, types), args, user);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                // 反射装配或未声明的受检错误属于程序错误，不伪装为业务失败。
                throw new IllegalStateException("服务反射调用失败：" + name, e);
            }
        });
    }
}
