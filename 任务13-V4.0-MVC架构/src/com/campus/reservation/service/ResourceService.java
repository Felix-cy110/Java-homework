package com.campus.reservation.service;

import com.campus.reservation.enums.ResourceStatus;
import com.campus.reservation.dao.DaoFactory;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.common.BusinessException;
import com.campus.reservation.exception.ResourceReservedException;
import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.dao.ResourceDao;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.campus.reservation.service.BusinessRules.requireAdministrator;
import static com.campus.reservation.service.BusinessRules.requireKnownUser;
import static com.campus.reservation.service.BusinessRules.isAdministrator;
import static com.campus.reservation.service.BusinessRules.validateResourceFields;
import static com.campus.reservation.service.BusinessRules.hasPassedReservation;
import static com.campus.reservation.service.BusinessRules.findResourceById;

/** 资源业务：维护权限、字段校验和 PASS 删除保护，不接触控制台或 IO 流。 */
public class ResourceService implements ResourceOperations {
    private static final ResourceService INSTANCE;
    private static final IOException INITIALIZATION_ERROR;

    // 构建唯一服务，并保留 DAO 初始化错误供启动入口处理。
    static {
        ResourceService instance = null;
        IOException error = null;
        try {
            instance = new ResourceService();
        } catch (IOException e) {
            error = e;
        }
        INSTANCE = instance;
        INITIALIZATION_ERROR = error;
    }
    private final ResourceDao resourceDao;
    private final ReserveRecordDao reserveDao;

    /** 通过工厂绑定共享 DAO。 */
    private ResourceService() throws IOException {
        this.resourceDao = DaoFactory.getResourceDao();
        this.reserveDao = DaoFactory.getReserveRecordDao();
    }

    /** 获取唯一服务实例。 */
    public static ResourceService getInstance() throws IOException {
        if (INITIALIZATION_ERROR != null) {
            throw INITIALIZATION_ERROR;
        }
        return INSTANCE;
    }

    /** 新增资源并在文件保存成功后更新内存集合。 */
    public Result<CampusResource> addResource(User user, String name, String type, String location) {
        try {
            List<CampusResource> resources = resourceDao.findAll();
            requireAdministrator(user);
            validateResourceFields(name, type, location, ResourceStatus.AVAILABLE.name());
            String id;
            while (true) {
                String candidateId = UUID.randomUUID().toString();
                boolean exists = Optional.ofNullable(resources).orElseGet(Collections::emptyList).stream()
                        .filter(Objects::nonNull).anyMatch(resource -> candidateId.equals(resource.getId()));
                if (!exists) {
                    id = candidateId;
                    break;
                }
            }
            CampusResource resource = new CampusResource(id, name, type, ResourceStatus.AVAILABLE.name(), location);
            resourceDao.insert(resource);
            return Result.success("资源新增成功：", resource);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成，请检查 resource.txt 的占用或访问权限。");
        }
    }

    /** 使用 Stream 组合角色过滤、名称模糊匹配和 ID 排序。 */
    public Result<List<CampusResource>> queryResources(User user, String keyword) {
        try {
            List<CampusResource> resources = resourceDao.findAll();
            requireKnownUser(user);
            String search = Optional.ofNullable(keyword).orElse("").trim().toLowerCase(Locale.ROOT);
            List<CampusResource> visible = Optional.ofNullable(resources).orElseGet(Collections::emptyList).stream()
                    .filter(Objects::nonNull)
                    .filter(resource -> isAdministrator(user) || ResourceStatus.AVAILABLE.name().equals(resource.getStatus()))
                    .filter(resource -> search.isEmpty()
                            || Optional.ofNullable(resource.getName()).orElse("")
                            .toLowerCase(Locale.ROOT).contains(search))
                    .sorted(Comparator.comparing(resource -> Optional.ofNullable(resource.getId()).orElse("")))
                    .collect(Collectors.toList());
            return Result.success(isAdministrator(user) ? "全部资源：" : "可预约资源：", visible);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        }
    }

    /** 修改资源基本信息，保存成功后替换内存中的同一条记录。 */
    public Result<CampusResource> updateResource(User user, String id, String name, String type,
            String location, String status) {
        try {
            requireAdministrator(user);
            Result<CampusResource> found = findResourceById(resourceDao, id);
            if (!found.isSuccess()) {
                return Result.fail("资源操作失败：" + found.getMsg());
            }
            CampusResource existing = found.getData();
            validateResourceFields(name, type, location, status);
            CampusResource changed = new CampusResource(existing.getId(), name, type, status, location);
            resourceDao.update(changed);
            return Result.success("资源修改成功：", changed);
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成，请检查 resource.txt 的占用或访问权限。");
        }
    }

    /** 只有存在 PASS 预约时禁止删除，PENDING/REJECT/CANCEL 不阻止删除。 */
    public Result<Void> deleteResource(User user, String id) {
        try {
            requireAdministrator(user);
            Result<CampusResource> found = findResourceById(resourceDao, id);
            if (!found.isSuccess()) {
                return Result.fail("资源操作失败：" + found.getMsg());
            }
            CampusResource existing = found.getData();
            if (hasPassedReservation(reserveDao.findAll(), existing.getId())) {
                throw new ResourceReservedException();
            }
            resourceDao.delete(existing.getId());
            return Result.success("资源删除成功。");
        } catch (BusinessException e) {
            return Result.fail("资源操作失败：" + e.getMessage());
        } catch (IOException e) {
            return Result.fail("系统错误：资源操作未完成，请检查 resource.txt 的占用或访问权限。");
        }
    }
}
