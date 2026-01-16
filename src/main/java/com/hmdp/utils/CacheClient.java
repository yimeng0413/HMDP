package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    //线程池，用来在逻辑过期方案中异步从数据库取出数据并写入redis
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 将任意java对象序列化为json并存储到Redis中（string类型），并设置过期时间TTL
     *
     * @param key
     * @param value
     * @param expireTime
     * @param timeUnit
     */
    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
    }

    /**
     * 将任意对象序列化成json并存储到Redis中（string类型），并设置逻辑过期时间
     *
     * @param key
     * @param value
     * @param expireTime
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        //将data数据封装到RedisData中（包含逻辑过期时间）
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        //将封装好的数据存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，缓存空数据解决缓存穿透问题
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param queryByDB 这里queryByDB的意思是： 我需要一个：给我ID，能得到R的方法，至于是什么方法，我不关心，调用者请你自己决定
     * @param expire    Function<ID, R>而且你看这个方法，这个前面的ID 就是function需要用到的参数，而后面的R是需要function返回的返回值
     * @param timeUnit
     * @param <R>       这个是Result的缩写  返回结果
     * @param <ID>      这个id，因为不一定传进来的id是long还是int等，所以这个id也用泛型接收
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> queryByDB, Long expire, TimeUnit timeUnit) {
        String redisKey = keyPrefix + id;                                 //这里Function的意思是： 我需要一个：给我ID，能得到R的方法，至于是什么方法，我不关心，调用者请你自己决定
        //从redis缓存
        String jsonData = stringRedisTemplate.opsForValue().get(redisKey);
        //如果命中，直接将结果（string)反序列化成对应对象返回
        if (StrUtil.isNotBlank(jsonData)) {
            R bean = JSONUtil.toBean(jsonData, type);
            return bean;
        }
        //如果命中“”，则直接返回null（这里是为了防止缓存穿透而保存的空对象“”）
        if (jsonData != null) {//这里不等于null，其实就是.equals("") （缓存空的时候会将value设置为“”，如果是blank，且非null，那么只有一种可能，就是“”）
            return null;
        }
        //这个就是执行这个Function的意思 (我需要一个：给我ID，能得到R的方法，至于是什么方法，我不关心，调用者请你自己传进来)
        R dbBean = queryByDB.apply(id);
        //如果DB也未命中，则缓存一个空值“”，避免缓存穿透
        if (dbBean == null) {
            stringRedisTemplate.opsForValue().set(redisKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //如果DB命中了，则将这个数据缓存到Redis，并且返回这个数据
        this.set(redisKey, dbBean, expire, timeUnit);
        return dbBean;
    }


    /**
     * \
     * 根据指定的key查询缓存，并反序列化为指定类型，逻辑过期解决缓存击穿问题
     *
     * @param keyPrefix
     * @param id
     * @param classType
     * @param queryByDB       这里queryByDB的意思是： 我需要一个：给我ID，能得到R的方法，至于是什么方法，我不关心，调用者请你自己决定
     * @param expire          Function<ID, R>而且你看这个方法，这个前面的ID 就是function需要用到的参数，而后面的R是需要function返回的返回值
     * @param timeUnit
     * @param <ID>            这个id，因为不一定传进来的id是long还是int等，所以这个id也用泛型接收
     * @param <R>这个是Result的缩写 返回结果
     * @return
     */
    public <ID, R> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> classType, Function<ID, R> queryByDB, Long expire, TimeUnit timeUnit) {
        String redisKey = keyPrefix + id;

        //从redis中尝试查数据
        String cachedData = stringRedisTemplate.opsForValue().get(redisKey);
        //理论上在这种方案中默认是一定能拿到缓存的，但是以防万一，这里先做个判断
        if (StrUtil.isBlank(cachedData)) {
            return null;
        }
        //从redis获取封装的数据后，从这个封装的数据中取出店铺数据和过期时间
        RedisData redisData = JSONUtil.toBean(cachedData, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //这里必须先转成JSONObject,然后再用hutool的toBean转成对应Bean 不然会报错 ClassCastException
        JSONObject data = (JSONObject) redisData.getData();
        R Cachedbean = JSONUtil.toBean(data, classType);
        //如果没过期，直接返回这个数据就行
        if (expireTime.isAfter(LocalDateTime.now())) {
            return Cachedbean;
        }
        //过期了，就开启一个线程，让他去执行获取数据库数据并且更新缓存的操作（需要获取锁）
        if (tryLock(LOCK_SHOP_KEY)) {
            //拿到锁了，那么就去数据库查数据(开启一个线程）
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            //从数据库中获取数据
                            R dbBean = queryByDB.apply(id);
                            //模拟数据库延迟
                            Thread.sleep(200);
                            //重建缓存
                            this.setWithLogicalExpire(redisKey, dbBean, expire, timeUnit);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            unlock(LOCK_SHOP_KEY);
                        }
                    }
            );
        }
        //无论拿没拿到锁，都返回脏数据（因为这个取数据重构缓存是异步的过程）
        return Cachedbean;
    }

    /**
     * 解决缓存击穿问题，用互斥锁  上锁（setNx 不存在，才能set进去）
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean isLocked = stringRedisTemplate.opsForValue().setIfAbsent(key, "", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isLocked);//涉及拆装箱，所以需要用hutool包处理一下
    }

    /**
     * 解决缓存击穿问题，互斥锁  释放锁（删除对应key）
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
