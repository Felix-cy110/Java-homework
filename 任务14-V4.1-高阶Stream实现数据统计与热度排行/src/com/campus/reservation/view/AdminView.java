package com.campus.reservation.view;

import com.campus.reservation.controller.ResourceController;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.entity.ResourceStatDTO;
import com.campus.reservation.entity.UserStatDTO;
import com.campus.reservation.entity.StatusStatDTO;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.IntStream;

/** 管理员视图：采集操作参数并展示报表，权限、状态和统计由服务处理。 */
public class AdminView extends ConsoleView {
    /** 沿用主视图的输入流和控制器。 */
    public AdminView(Scanner scanner, ResourceController resourceController,
            ReserveController reserveController) {
        super(scanner, resourceController, reserveController);
    }

    /** 保留原菜单编号，追加四个管理员统计入口。 */
    @Override
    protected void printMenu() {
        System.out.println("管理员菜单：1.新增资源  2.查询资源  3.修改资源  4.删除资源"
                + "  5.查看全部预约  6.审核预约  7.筛选预约  8.查看待审核预约"
                + "  9.资源预约统计  10.热门资源TOP排行  11.用户预约统计  12.全局状态汇总  0.退出登录");
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
            case "9": displayResourceStatistics(reserveController.queryResourceStatistics(user)); break;
            case "10": displayResourceStatistics(readHotResources(user)); break;
            case "11": displayUserStatistics(reserveController.queryUserStatistics(user)); break;
            case "12": displayStatusStatistics(reserveController.queryStatusStatistics(user)); break;
            default: return false;
        }
        return true;
    }

    /** 界面只转换整数；正数限制在服务中校验，防止绕过视图。 */
    private Result<List<ResourceStatDTO>> readHotResources(User user) {
        String input = readRequiredInput("请输入 TOP 条数（正整数）：");
        try {
            return reserveController.queryHotResources(user, Integer.parseInt(input));
        } catch (NumberFormatException e) {
            return Result.fail("操作失败：TOP 条数必须为有效正整数，且不超过 2147483647。");
        }
    }

    /** 输出服务给定的顺序和比率，不在视图重新统计或排序。 */
    private void displayResourceStatistics(Result<List<ResourceStatDTO>> result) {
        if (!startReport(result)) { return; }
        System.out.println("序号 | 资源ID | 资源名称 | 总申请 | 通过 | 驳回 | 取消 | 通过率");
        List<ResourceStatDTO> rows = result.getData();
        IntStream.range(0, rows.size()).forEach(index -> {
            ResourceStatDTO row = rows.get(index);
            System.out.printf(Locale.ROOT, "%d | %s | %s | %d | %d | %d | %d | %.2f%%%n",
                    index + 1, row.getResourceId(), row.getResourceName(), row.getTotalApplyCount(),
                    row.getPassCount(), row.getRejectCount(), row.getCancelCount(), row.getPassRate() * 100);
        });
    }

    /** 用户报表只展示账号和数量，不包含密码等账号字段。 */
    private void displayUserStatistics(Result<List<UserStatDTO>> result) {
        if (!startReport(result)) { return; }
        System.out.println("申请人账号 | 总提交 | 通过 | 驳回");
        result.getData().forEach(row -> System.out.printf(Locale.ROOT, "%s | %d | %d | %d%n",
                row.getApplicantUsername(), row.getTotalApplyCount(), row.getPassCount(), row.getRejectCount()));
    }

    /** 四种状态及零数量均由服务返回，视图逐行展示。 */
    private void displayStatusStatistics(Result<List<StatusStatDTO>> result) {
        if (!startReport(result)) { return; }
        System.out.println("预约状态 | 数量");
        result.getData().forEach(row -> System.out.printf(Locale.ROOT, "%s | %d%n", row.getStatus(), row.getCount()));
    }

    /** 报表共用结果提示，失败或空列表不继续打印表格。 */
    private boolean startReport(Result<? extends List<?>> result) {
        System.out.println(result.getMsg());
        if (!result.isSuccess()) { return false; }
        if (result.getData().isEmpty()) {
            System.out.println("暂无符合条件的记录。");
            return false;
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
