package com.campus.reservation.common.util;

import com.campus.reservation.common.BusinessException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** 原生反射对象工具：读取 JavaBean 属性、浅拷贝兼容属性和检查成员空值，不包含业务规则。 */
public final class ObjectReflectUtil {
    /** 静态工具不允许外部实例化。 */
    private ObjectReflectUtil() { }

    /** 通过标准 getter 输出属性；顶层 null 返回空串，属性 null 保留字面 null。 */
    public static String objectToString(Object obj) {
        return objectToString(obj, new String[0]);
    }

    /** 日志可额外指定敏感属性名，递归遮蔽且不调用其 getter；通用方法不绑定具体实体。 */
    public static String objectToString(Object obj, String... sensitiveProperties) {
        if (obj == null) { return ""; }
        Set<String> masked = new HashSet<>();
        if (sensitiveProperties != null) { masked.addAll(Arrays.asList(sensitiveProperties)); }
        Set<Object> path = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        return describe(obj, masked, path, 0);
    }

    /** 创建目标并浅拷贝同名、赋值兼容的属性；缺失或类型不匹配的属性直接跳过。 */
    public static <T> T copyProperties(Object source, Class<T> targetCls) {
        if (source == null) { return null; }
        if (targetCls == null) { throw new BusinessException("对象复制失败：请提供目标类型。"); }
        T target;
        try {
            // 常规实体提供无参构造；不能构造时只暴露业务提示，不传播反射堆栈。
            target = targetCls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new BusinessException("对象复制失败：目标类型需要可访问的无参构造方法。");
        }
        try {
            List<Method> setters = new ArrayList<>(Arrays.asList(targetCls.getMethods()));
            setters.sort(Comparator.comparing(Method::toGenericString));
            for (Map.Entry<String, Method> entry : getters(source.getClass()).entrySet()) {
                Method getter = entry.getValue();
                Method setter = findSetter(setters, targetCls, entry.getKey(), getter.getReturnType());
                if (setter == null) { continue; }
                try {
                    Object value = getter.invoke(source);
                    if (value == null && setter.getParameterTypes()[0].isPrimitive()) { continue; }
                    setter.invoke(target, value);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // 单个属性访问失败时保留目标默认值，继续复制其他兼容属性。
                }
            }
        } catch (RuntimeException e) {
            throw new BusinessException("对象复制失败：无法读取对象属性。");
        }
        return target;
    }

    /** 检查本类及父类非静态成员；null/无成员对象为 true，基本类型的 0/false 不等于 null。 */
    public static boolean isAllFieldNull(Object obj) {
        if (obj == null) { return true; }
        try {
            for (Class<?> type = obj.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) { continue; }
                    field.setAccessible(true);
                    if (field.get(obj) != null) { return false; }
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new BusinessException("对象空值检查失败：无法访问对象成员。");
        }
    }

    /** 统一处理简单值、容器及 Bean；身份集合和深度上限防止循环引用拖垮日志。 */
    private static String describe(Object obj, Set<String> masked, Set<Object> path, int depth) {
        if (obj == null) { return "null"; }
        if (obj instanceof CharSequence || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || obj instanceof Enum<?> || obj instanceof TemporalAccessor
                || obj instanceof TemporalAmount || obj instanceof UUID) { return fallback(obj, false); }
        if (depth >= 32) { return "[层级过深]"; }
        if (!path.add(obj)) { return "[循环引用]"; }
        try {
            List<String> parts = new ArrayList<>();
            if (obj.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(obj); i++) {
                    parts.add(describe(Array.get(obj, i), masked, path, depth + 1));
                }
                return "[" + String.join(", ", parts) + "]";
            }
            if (obj instanceof Iterable<?>) {
                for (Object item : (Iterable<?>) obj) { parts.add(describe(item, masked, path, depth + 1)); }
                return "[" + String.join(", ", parts) + "]";
            }
            if (obj instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                    String key = describe(entry.getKey(), masked, path, depth + 1);
                    parts.add(key + "=" + (masked.contains(key) ? "[已脱敏]"
                            : describe(entry.getValue(), masked, path, depth + 1)));
                }
                return "{" + String.join(", ", parts) + "}";
            }
            Map<String, Method> properties = getters(obj.getClass());
            if (properties.isEmpty()) { return fallback(obj, !masked.isEmpty()); }
            for (Map.Entry<String, Method> entry : properties.entrySet()) {
                String name = entry.getKey();
                parts.add(name + "=" + (masked.contains(name) ? "[已脱敏]"
                        : describe(entry.getValue().invoke(obj), masked, path, depth + 1)));
            }
            return obj.getClass().getSimpleName() + "{" + String.join(", ", parts) + "}";
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 默认按任务要求回退 toString；敏感日志不能回退到可能含密码的实体 toString。
            return fallback(obj, !masked.isEmpty());
        } finally { path.remove(obj); }
    }

    /** 收集公有标准 getter（含父类和 boolean isX），排序保证输出稳定，跳过静态/桥接成员。 */
    private static Map<String, Method> getters(Class<?> type) {
        List<Method> methods = new ArrayList<>(Arrays.asList(type.getMethods()));
        methods.sort(Comparator.comparing(Method::getName).thenComparing(Method::toGenericString));
        Map<String, Method> result = new java.util.TreeMap<>();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || method.getParameterCount() != 0 || method.getReturnType() == void.class
                    || method.getName().equals("getClass")) { continue; }
            String suffix = null;
            if (method.getName().startsWith("get") && method.getName().length() > 3) {
                suffix = method.getName().substring(3);
            } else if (method.getName().startsWith("is") && method.getName().length() > 2
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                suffix = method.getName().substring(2);
            }
            if (suffix == null) { continue; }
            String name = propertyName(suffix);
            if (!staticField(type, name)) { result.put(name, method); }
        }
        return result;
    }

    /** 首字母规则保留 URL 等连续大写属性名。 */
    private static String propertyName(String name) {
        return name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))
                ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** 成员 getter 若对应静态字段也跳过；计算属性没有同名字段时正常保留。 */
    private static boolean staticField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return Modifier.isStatic(current.getDeclaredField(name).getModifiers()); }
            catch (NoSuchFieldException e) { /* 查找父类声明，不把缺少同名字段当作失败。 */ }
        }
        return false;
    }

    /** 优先精确类型，其次安全赋值兼容；不做字符串转换或可能丢精度的数字转换。 */
    private static Method findSetter(List<Method> methods, Class<?> type, String name, Class<?> sourceType) {
        Method compatible = null;
        if (staticField(type, name)) { return null; }
        for (Method method : methods) {
            String methodName = method.getName();
            if (!methodName.startsWith("set") || methodName.length() <= 3 || method.getParameterCount() != 1
                    || Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || !name.equals(propertyName(methodName.substring(3)))) { continue; }
            Class<?> targetType = method.getParameterTypes()[0];
            if (targetType == sourceType) { return method; }
            if (boxed(targetType).isAssignableFrom(boxed(sourceType)) && compatible == null) { compatible = method; }
        }
        return compatible;
    }

    /** 支持基本类型与其包装类互相赋值，空包装值不会写入基本类型。 */
    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) { return type; }
        if (type == boolean.class) { return Boolean.class; }
        if (type == char.class) { return Character.class; }
        if (type == byte.class) { return Byte.class; }
        if (type == short.class) { return Short.class; }
        if (type == int.class) { return Integer.class; }
        if (type == long.class) { return Long.class; }
        if (type == float.class) { return Float.class; }
        if (type == double.class) { return Double.class; }
        return type;
    }

    /** toString 自身也可能失败；不外泄异常，最终回退 Object 风格的类名和身份标识。 */
    private static String fallback(Object obj, boolean opaque) {
        if (!opaque) {
            try { return String.valueOf(obj); } catch (RuntimeException e) { /* 继续安全降级。 */ }
        }
        return obj.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(obj));
    }
}
