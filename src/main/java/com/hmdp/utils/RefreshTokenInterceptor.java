package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 登录拦截器。如果用户没登陆，则拦截
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 前置拦截器
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        /*//从request对象中获取session
        HttpSession session = request.getSession();
        //看看session中是否有user（是否授权）
        Object user = session.getAttribute("user");*/
        //从request对象中获取请求头，这个在前端中做了处理，他会将token写到请求头中，key为authorization
        String token = request.getHeader("authorization");
        //如果token是空的话，不用尝试去redis看了，直接返回未授权状态码即可
        if (StrUtil.isBlank(token)) {
            /*//用户不存在，拦截，返回401状态码(未授权)
            response.setStatus(401);//一般拦截之后，就会用response告诉前端，或者直接response.redirect这样跳转到别的页面，再或者抛个异常交给异常处理器处理！*/
            return true;
        }
        //从redis中获取对象
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (entries.isEmpty()) {//这里如果在redis取不到值的话，不会返回null，而是空 所以用isEmpty来判断
            /*//用户不存在，拦截，返回401状态码(未授权)
            response.setStatus(401);*/
            return true;
        }
        //将获取的对象转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        //如果用户存在，则将用户存入threadLocal（线程隔离，方面这个线程的后续那些处理使用这个user情报）
        UserHolder.saveUser(userDTO);
        //刷新在redis中的user信息有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    /**
     * 后置处理，（处理都完事之后被拦截）
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //删除user信息，避免内存泄露
        UserHolder.removeUser();
    }
}
