package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jdk.nashorn.internal.ir.CallNode;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    @Autowired
    UserMapper userMapper;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误。");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(4);
        /*//将生成的验证码存放到session
        session.setAttribute("code", code);*/
        //将生成的验证码放到redis（这里要加上有效期 过期自动从redis中删除）
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码(模拟)
        log.debug("发送验证码成功，验证码：{}", code);
        return Result.ok();
    }

    /**
     * 登录，注册功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //拿到用户输入的手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误。");
        }
        //用户输入的验证码
        String code = loginForm.getCode();
        /*//session中存放的验证码
        String cacheCode = (String) session.getAttribute("code");*/
        //redis中存放的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        //比较验证码，如果不一致则返回错误信息
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码不一致。");
        }
        //拿着手机号去数据库查
        User user = userMapper.selectUser(phone);
        //如果数据库中没有这个用户，则新插入该用户
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICKNAME_PFEFIX + RandomUtil.randomString(10));
            userMapper.insertUser(user);
        }

        //用userDTO（没有敏感信息）代替user
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        /*//将用户信息存入session
        session.setAttribute("user", userDTO);*/
        //用uuid生成一个唯一标识（token）
        String token = UUID.randomUUID().toString(true);//这个true代表不带中划线
        //将对象转换成Map，方便一下子全部存入redis的hash结构中
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()//因为这个UserDTO中的 id是Long类型，而StringRedisTemplate extends RedisTemplate<String, String>，所以需要把这个long改成string 不然发生ClassCastException
                        .setIgnoreNullValue(true)//忽略null
                        .setFieldValueEditor((key, value) -> value.toString()));//这个就是把value转换成string
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, stringObjectMap);
        //设置个过期时间 30分钟（和session默认过期时间一样）
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }
}
