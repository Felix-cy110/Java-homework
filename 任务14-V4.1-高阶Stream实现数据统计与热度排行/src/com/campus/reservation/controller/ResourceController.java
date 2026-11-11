package com.campus.reservation.controller;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.ResourceOperations;
import com.campus.reservation.service.ResourceService;
import java.io.IOException;
import java.util.List;

/** 资源控制器：转发资源操作，权限和资源规则由原服务执行。 */
public class ResourceController extends ControllerSupport {
    private final ResourceOperations resourceService;

    /** 获取资源服务单例，沿用其加载失败语义。 */
    public ResourceController() throws IOException {
        resourceService = ResourceService.getInstance();
    }

    /** 转发新增资源及未经控制器加工的字段。 */
    public Result<CampusResource> addResource(User user, String name, String type, String location) {
        return execute(() -> resourceService.addResource(user, name, type, location));
    }

    /** 转发查询，服务根据身份决定资源可见范围。 */
    public Result<List<CampusResource>> queryResources(User user, String keyword) {
        return execute(() -> resourceService.queryResources(user, keyword));
    }

    /** 转发修改，不在控制器中改变资源状态。 */
    public Result<CampusResource> updateResource(User user, String id, String name,
            String type, String location, String status) {
        return execute(() -> resourceService.updateResource(user, id, name, type, location, status));
    }

    /** 转发删除，预约关联校验仍由服务负责。 */
    public Result<Void> deleteResource(User user, String id) {
        return execute(() -> resourceService.deleteResource(user, id));
    }
}
