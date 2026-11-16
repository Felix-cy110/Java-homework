package com.campus.reservation.controller;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.ResourceService;
import com.campus.reservation.service.ResourceServiceImpl;
import java.io.IOException;
import java.util.List;

/** 资源控制器：经反射切面调用服务，转发资源操作，权限和资源规则由原服务执行。 */
public class ResourceController extends ControllerSupport {
    private final ResourceService resourceService;

    /** 获取资源服务单例，沿用其加载失败语义。 */
    public ResourceController() throws IOException {
        resourceService = ResourceServiceImpl.getInstance();
    }

    /** 转发新增资源及未经控制器加工的字段。 */
    public Result<CampusResource> addResource(User user, String name, String type, String location) {
        return invoke(resourceService, "addResource", new Class<?>[] {User.class, String.class, String.class, String.class}, user, user, name, type, location);
    }

    /** 转发查询，服务根据身份决定资源可见范围。 */
    public Result<List<CampusResource>> queryResources(User user, String keyword) {
        return invoke(resourceService, "queryResources", new Class<?>[] {User.class, String.class}, user, user, keyword);
    }

    /** 转发修改，不在控制器中改变资源状态。 */
    public Result<CampusResource> updateResource(User user, String id, String name,
            String type, String location, String status) {
        return invoke(resourceService, "updateResource", new Class<?>[] {User.class, String.class, String.class, String.class, String.class, String.class}, user, user, id, name, type, location, status);
    }

    /** 转发删除，预约关联校验仍由服务负责。 */
    public Result<Void> deleteResource(User user, String id) {
        return invoke(resourceService, "deleteResource", new Class<?>[] {User.class, String.class}, user, user, id);
    }
}
