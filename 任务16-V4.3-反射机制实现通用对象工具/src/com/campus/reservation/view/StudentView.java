package com.campus.reservation.view;

import com.campus.reservation.controller.ResourceController;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import java.time.LocalDateTime;
import java.util.Scanner;

/** 学生视图：收集预约申请与取消输入，展示自己的查询结果。 */
public class StudentView extends ConsoleView {
    /** 复用主视图的输入流和控制器，不持有服务实例。 */
    public StudentView(Scanner scanner, ResourceController resourceController,
            ReserveController reserveController) {
        super(scanner, resourceController, reserveController);
    }

    /** 保留学生菜单的文字和编号。 */
    @Override
    protected void printMenu() {
        System.out.println("学生菜单：2.查询可预约资源  5.提交预约  6.我的预约"
                + "  7.取消预约  0.退出登录");
    }

    /** 按输入编号请求控制器，不在界面判断预约权限。 */
    @Override
    protected boolean handleChoice(String choice, User user) {
        switch (choice) {
            case "5": displayResult(submitReservationFromInput(user)); break;
            case "6": displayResult(reserveController.queryMyReservations(user)); break;
            case "7":
                String id = readRequiredInput("请输入要取消的预约记录 ID：");
                displayResult(reserveController.cancelReservation(user, id));
                break;
            default: return false;
        }
        return true;
    }

    /** 转换时间输入，申请时间范围和资源状态由服务校验。 */
    private Result<ReserveRecord> submitReservationFromInput(User user) {
        String resourceId = readRequiredInput("请输入资源 ID：");
        LocalDateTime start = parseDateTime(readRequiredInput("请输入开始时间（yyyy-MM-ddTHH:mm）："), "开始时间");
        LocalDateTime end = parseDateTime(readRequiredInput("请输入结束时间（yyyy-MM-ddTHH:mm）："), "结束时间");
        String remark = readInput("请输入备注（可留空）：");
        return reserveController.submitReservation(user, resourceId, start, end, remark);
    }
}
