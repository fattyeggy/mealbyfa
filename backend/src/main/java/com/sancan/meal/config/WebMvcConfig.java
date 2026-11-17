package com.sancan.meal.config;

import com.sancan.meal.common.security.AuthInterceptor;
import com.sancan.meal.common.security.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 请求拦截配置：后台与小程序接口均需令牌；
 * 登录接口与健康检查放行。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, PermissionInterceptor permissionInterceptor) {
        this.authInterceptor = authInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/admin/**", "/app/**")
                .excludePathPatterns(
                        "/admin/auth/login",
                        "/admin/index.html",
                        "/admin/assets/**",
                        "/admin/favicon.ico",
                        "/app/auth/login",
                        // 公共周菜单：未注册用户首页必须可浏览（手册 §3）
                        "/app/menu/**",
                        "/ping",
                        "/error",
                        "/actuator/**");
        // 权限拦截必须排在鉴权之后，依赖 UserContext 里的角色
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/auth/login");

    }
}
