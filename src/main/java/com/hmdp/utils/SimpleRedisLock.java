package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.apache.tomcat.jni.Lock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 你知道吗 刚开始我打算写@Component 交给Spring管理的
 * 遇到了问题： 正常注入这个类的时候，需要指定业务前缀吧（因为不可能各种业务场景我们都公用一把锁）
 * 问题就是spring这种注入，默认都是单例的 原则：适合无状态/全局共享的对象
 * 而这个lock是：每个业务有他特定前缀的，并不是全局共享的对象。
 * 加入我shop的时候注入了这个类，那这时候key就是lock:shop:userId:xxxx
 * 紧接着我有个order业务也注入这个类，那这时候key就被覆盖成lock:order:userId:xxxx 乱套了
 * 所以 单例+可变状态就是风险 我的做法就是这里不交给spring管理，需要的时候就在具体业务中new出来使用即可
 */
public class SimpleRedisLock implements ILock {

    StringRedisTemplate stringRedisTemplate;

    String keyPrefix;

    String ID_PREFIX = UUID.randomUUID().toString(true);

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String keyPrefix) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyPrefix = keyPrefix;
    }

    /**
     * 尝试获取锁  这里有个细节是 锁的value一般用线程ID即可  然后Thread.currentThread().getId()这个是Long，所以通过+“”就变成String了
     *
     * @param timeoutSec 锁过期时间(TTL)
     * @return 从redis尝试获取锁；如果是true，返回ture  如果是false或者null，返回false
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //改进一下，解决误删key的问题。具体做法是set的时候不光存储当前线程ID，还拼接上UUID（因为不同JVM这个threadId可能会重复）
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK + keyPrefix, ID_PREFIX + Thread.currentThread().getId(), timeoutSec, TimeUnit.SECONDS);
        //redis返回的是Boolean包装类 我们方法是boolean 直接拆箱，有可能空指针   所以用这个hutool的封装一下，这个Boolean.TRUE一定不会空指针对吧 然后拿着这个去.equals
        //结果就是： 如果是true，返回ture  如果是false或者null，返回false
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock() {
        String redisValue = stringRedisTemplate.opsForValue().get(RedisConstants.LOCK + keyPrefix);
        String currentValue = ID_PREFIX + Thread.currentThread().getId();
        //做了改进，解决key误删问题  删除之前先判断是不是自己的那个key，如果是，才去删除；如果不是，啥都不做
        if(currentValue.equals(redisValue)){
            stringRedisTemplate.delete(RedisConstants.LOCK + keyPrefix);
        }
    }
}
