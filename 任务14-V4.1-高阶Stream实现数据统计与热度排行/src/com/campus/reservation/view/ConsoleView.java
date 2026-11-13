package com.campus.reservation.view;

import com.campus.reservation.controller.ResourceController;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.common.BusinessException;
import com.campus.reservation.exception.EmptyParameterException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/** 共享控制台输入输出和角色菜单循环，不访问业务服务或文件。 */
abstract class ConsoleView {
    protected final Scanner scanner;
    protected final ResourceController resourceController;
    protected final ReserveController reserveController;

    /** 共享同一输入流；只由主视图负责关闭 Scanner。 */
    protected ConsoleView(Scanner scanner, ResourceController resourceController,
            ReserveController reserveController) {
        this.scanner = scanner;
        this.resourceController = resourceController;
        this.reserveController = reserveController;
    }

    /** 执行角色菜单；退出登录返回 true，输入结束返回 false。 */
    public boolean start(User user) {
        while (true) {
            boolean choosingMenu = true;
            try {
                printMenu();
                String choice = readInput("请选择：");
                if (choice == null) {
                    return false;
                }
                choosingMenu = false;
                choice = choice.trim();
                if ("0".equals(choice)) {
                    System.out.println("已退出登录。");
                    return true;
                }
                if ("2".equals(choice)) {
                    String keyword = readInput("请输入资源名称关键词（可留空）：");
                    if (keyword == null) {
                        return false;
                    }
                    displayResult(resourceController.queryResources(user, keyword));
                } else if (!handleChoice(choice, user)) {
                    throw new BusinessException("无效菜单选项，请按当前角色菜单输入。");
                }
            } catch (BackNavigation e) {
                // 菜单返回首页；表单取消只回当前角色菜单，不提交半成品。
                if (choosingMenu) {
                    System.out.println("已退出登录。");
                    return true;
                }
            } catch (BusinessException e) {
                displayResult(Result.fail("操作失败：" + e.getMessage()));
            }
        }
    }

    /** 子视图提供当前角色的菜单文字。 */
    protected abstract void printMenu();

    /** 子视图只收集菜单参数和转发；返回是否识别该选项。 */
    protected abstract boolean handleChoice(String choice, User user);

    /** 展示统一结果，空列表仍显示基线中的提示。 */
    protected void displayResult(Result<?> result) {
        System.out.println(result.getMsg());
        if (!result.isSuccess()) {
            return;
        }
        Object data = result.getData();
        if (data instanceof List<?>) {
            List<?> items = (List<?>) data;
            if (items.isEmpty()) {
                System.out.println("暂无符合条件的记录。");
            }
            items.forEach(System.out::println);
        } else if (data instanceof User) {
            System.out.println("用户信息：" + data);
        } else if (data != null) {
            System.out.println(data);
        }
    }

    /** 读取一行，EOF 交给菜单结束会话。 */
    protected String readInput(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println("输入已结束，程序退出。");
            return null;
        }
        String value = scanner.nextLine();
        // 所有字段在解析类型和调用业务前识别返回，包括可空字段。
        if ("back".equals(value.trim())) {
            throw new BackNavigation();
        }
        return literalInput(value);
    }

    /** 校验输入格式；字段的业务约束仍交给控制器与服务。 */
    protected String readRequiredInput(String prompt) {
        String value = readInput(prompt);
        if (value == null) {
            throw new BusinessException("输入已结束。");
        }
        if (value.trim().isEmpty()) {
            throw new EmptyParameterException("输入项");
        }
        return value.trim();
    }

    /** 将界面时间文本转换为模型参数，保留原错误提示。 */
    protected LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldName + "格式错误，请使用 yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-ddTHH:mm。");
        }
    }

    /** 反斜杠转义保留字，已有账号或字段为 back 时仍可按原值输入。 */
    protected static String literalInput(String value) {
        int prefix = 0;
        while (prefix < value.length() && value.charAt(prefix) == '\\') {
            prefix++;
        }
        return prefix > 0 && "back".equals(value.substring(prefix).trim())
                ? value.substring(1) : value;
    }
}
