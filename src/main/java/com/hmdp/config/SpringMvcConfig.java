package com.hmdp.config;

import com.hmdp.utils.RefreshTokenInterceptor;
import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpringMvcConfig implements WebMvcConfigurer {

    @Autowired
    RefreshTokenInterceptor refreshTokenInterceptor;

    @Autowired
    LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录的拦截器 执行顺序：2 （1→2→3...）
        registry.addInterceptor(loginInterceptor).excludePathPatterns(
                "/shop/**",
                "/voucher/**",
                "/shop-type/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        ).order(1);//这个先后顺序 可以用order定，也可以写先后顺序，会按照从上到下的顺序执行拦截。
        //刷新token的拦截器 执行顺序：1
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);//order越小，越先执行 。
    }
}
