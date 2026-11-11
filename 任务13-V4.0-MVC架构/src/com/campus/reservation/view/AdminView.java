package com.campus.reservation.view;

import com.campus.reservation.controller.ResourceController;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** 管理员视图：采集资源和审核输入，不执行权限或状态流转。 */
public class AdminView extends ConsoleView {
    /** 沿用主视图的输入流和控制器。 */
    public AdminView(Scanner scanner, ResourceController resourceController,
            ReserveController reserveController) {
        super(scanner, resourceController, reserveController);
    }

    /** 展示资源管理和预约审核选项。 */
    @Override
    protected void printMenu() {
        System.out.println("管理员菜单：1.新增资源  2.查询资源  3.修改资源  4.删除资源"
                + "  5.查看全部预约  6.审核预约  7.筛选预约  8.查看待审核预约  0.退出登录");
    }

    /** 只按菜单编号分发控制器请求。 */
    @Override
    protected boolean handleChoice(String choice, User user) {
        switch (choice) {
            case "1": case "3": case "4":
                handleResourceOperation(user, choice);
                break;
            case "5": displayResult(reserveController.queryAllReservations(user)); break;
            case "6": displayResult(auditReservationFromInput(user)); break;
            case "7": displayResult(filterReservationsFromInput(user)); break;
            case "8": displayResult(reserveController.queryPendingReservations(user)); break;
            default: return false;
        }
        return true;
    }

    /** 收集资源字段，将原始业务参数交给控制器。 */
    private void handleResourceOperation(User user, String choice) {
        if ("1".equals(choice)) {
            String name = readRequiredInput("请输入资源名称：");
            String type = readRequiredInput("请输入资源类型（教室/实验室/设备/场地）：");
            String location = readInput("请输入位置（可留空）：");
            displayResult(resourceController.addResource(user, name, type, location));
            return;
        }
        String id = readRequiredInput("请输入资源 ID：");
        if ("4".equals(choice)) {
            displayResult(resourceController.deleteResource(user, id));
            return;
        }
        String name = readRequiredInput("请输入资源名称：");
        String type = readRequiredInput("请输入资源类型（教室/实验室/设备/场地）：");
        String location = readInput("请输入位置（可留空）：");
        String status = readRequiredInput("请输入状态（AVAILABLE/OCCUPIED/DISABLED）：");
        displayResult(resourceController.updateResource(user, id, name, type, location, status));
    }

    /** 转换审核输入为枚举，驳回时额外显示原因输入框。 */
    private Result<ReserveRecord> auditReservationFromInput(User user) {
        String recordId = readRequiredInput("请输入预约记录 ID：");
        String decision = readRequiredInput("请输入审核结果（PASS/REJECT）：").toUpperCase(Locale.ROOT);
        ReserveStatus status;
        try {
            status = ReserveStatus.valueOf(decision);
        } catch (IllegalArgumentException e) {
            return Result.fail("操作失败：审核结果只能为 PASS 或 REJECT。");
        }
        String reason = status == ReserveStatus.REJECT ? readInput("请输入驳回原因（可留空）：") : "";
        return reserveController.auditReservation(user, recordId, status, reason);
    }

    /** 将可选的状态文本转成枚举；实际筛选在服务层进行。 */
    private Result<List<ReserveRecord>> filterReservationsFromInput(User user) {
        String resourceId = readInput("资源 ID（可留空）：");
        String applicant = readInput("申请人账号（可留空）：");
        String statusText = readInput("状态（PENDING/PASS/REJECT/CANCEL，可留空）：");
        ReserveStatus status = null;
        if (statusText != null && !statusText.trim().isEmpty()) {
            try {
                status = ReserveStatus.valueOf(statusText.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Result.fail("操作失败：状态只能为 PENDING、PASS、REJECT 或 CANCEL。");
            }
        }
        return reserveController.filterReservations(user, resourceId, applicant, status);
    }
}
