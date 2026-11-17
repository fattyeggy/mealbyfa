package com.sancan.meal.common.security;

import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 令牌鉴权拦截器。请求头格式与 RuoYi 一致：{@code Authorization: Bearer {token}}。
 *
 * <p>除认证外还做端点域校验：{@code /admin/**} 只接受后台账号，
 * {@code /app/**} 只接受小程序会员，防止两类令牌互相越权。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        LoginUser user = tokenStore.resolve(token)
                .orElseThrow(() -> new ServiceException(ErrorCode.UNAUTHORIZED));
        checkScope(request.getRequestURI(), user);
        UserContext.set(user);
        return true;
    }

    private void checkScope(String uri, LoginUser user) {
        if (uri.startsWith("/admin/") && user.getUserType() != UserType.ADMIN) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "该接口仅限后台账号访问");
        }
        if (uri.startsWith("/app/") && user.getUserType() != UserType.MEMBER) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "该接口仅限小程序会员访问");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
