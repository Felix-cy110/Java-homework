package com.campus.reservation.view;

/** 仅在输入与菜单之间传递返回请求，不代表业务失败，也不进入业务层。 */
final class BackNavigation extends RuntimeException {
    private static final long serialVersionUID = 1L;
}
