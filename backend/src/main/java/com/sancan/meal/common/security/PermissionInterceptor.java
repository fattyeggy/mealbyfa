package com.sancan.meal.common.security;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 后台权限拦截：读取方法上的 {@link RequirePermission}，与当前登录角色比对。
 * 未标注权限的后台接口按「登录即可」处理，因此敏感接口必须显式标注。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = AnnotationUtils.findAnnotation(
                handlerMethod.getMethod(), RequirePermission.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        Role role = user.getRoles().stream()
                .map(Role::of)
                .findFirst()
                .orElse(Role.OPS);
        boolean allowed = Arrays.stream(annotation.value()).anyMatch(role::has);
        if (!allowed) {
            throw new ServiceException(ErrorCode.FORBIDDEN,
                    "当前角色（" + role.getLabel() + "）无权执行该操作");
        }
        return true;
    }
}
