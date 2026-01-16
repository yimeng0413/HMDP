package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl implements IShopService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopMapper shopMapper;

    //线程池，用来在逻辑过期方案中异步从数据库取出数据并写入redis
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据ID查询店铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryShopById(Long id) {
//        Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺信息不存在");
        }

        return Result.ok(shop);

    }

    /**
     * 逻辑过期解决缓存击穿  将商铺信息封装成RedisData（带逻辑过期时间）存到redis（这个方案默认这种热点KEY一直是存在与redis中的）
     *
     * @param id
     * @param expireTime
     */
    public void saveShopDataToRedis(Long id, Long expireTime) {
        //从数据库查shop信息
        Shop shop = shopMapper.queryShopById(id);
        if (shop == null) {
            return;
        }
        /*try {
            //模拟延迟
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }*/
        //将shop信息封装到redisData对象（这里除了shop信息，还加了一个逻辑过期时间，这样做的目的是不侵入原代码）
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //将redisData存到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 逻辑过期方案解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Shop queryWithLogicalExpire(Long id) {
        //从redis中尝试查数据
        String cachedShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //理论上在这种方案中默认是一定能拿到缓存的，但是以防万一，这里先做个判断
        if (StrUtil.isBlank(cachedShop)) {
            return null;
        }
        //从redis获取封装的数据后，从这个封装的数据中取出店铺数据和过期时间
        RedisData redisData = JSONUtil.toBean(cachedShop, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //这里必须先转成JSONObject,然后再用hutool的toBean转成shop 不然会报错 ClassCastException
        JSONObject data = (JSONObject) redisData.getData();
        Shop cacheShop = JSONUtil.toBean(data, Shop.class);
        //如果没过期，直接返回这个数据就行
        if (expireTime.isAfter(LocalDateTime.now())) {
            return cacheShop;
        }
        //过期了，就开启一个线程，让他去执行获取数据库数据并且更新缓存的操作（需要获取锁）
        if (tryLock(LOCK_SHOP_KEY)) {
            //拿到锁了，那么就去数据库查数据(开启一个线程）
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            //重建缓存
                            this.saveShopDataToRedis(id, 20L);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            unlock(LOCK_SHOP_KEY);
                        }
                    }
            );
        }
        //无论拿没拿到锁，都返回脏数据（因为这个取数据重构缓存是异步的过程）
        return cacheShop;
    }

    /**
     * 互斥锁解决缓存击穿问题（部分热点key）
     *
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id) {
        //从redis中尝试查数据
        String cachedShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(cachedShop)) {
            //命中，直接返回
            Shop shop = JSONUtil.toBean(cachedShop, Shop.class);
            return shop;
        }
        //如果命中了缓存空数据，则返回店铺信息不存在（解决缓存穿透）
        if ("".equals(cachedShop)) {
            //return Result.fail("店铺信息不存在（命中了缓存空数据）");
            return null;
        }

        //未命中，先尝试获取锁
        Shop shop = null;
        try {
            //没获取到锁，等待一会，然后重新去redis缓存尝试获取数据
            if (!tryLock(LOCK_SHOP_KEY + id)) {
                Thread.sleep(50);//毫秒
                return queryWithMutex(id); //这个return,在这里写不写都一样  递归中，如果只是为了重复执行，不一定return 但是递归的结果想被上一层用到，那肯定得return对吧
            }
            //取到锁了，从数据库中获取数据
            shop = shopMapper.queryShopById(id);
            //模拟数据库延迟
            //Thread.sleep(200);//这个单位是毫秒
            if (shop == null) {
                //如果数据库也没有，缓存空到redis，解决缓存穿透
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //将查询到的数据添加到redis缓存
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY + id);
        }
        //将查询到的数据添加到redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;

    }


    /**
     * 解决缓存穿透方案（缓存一个“”（空））
     *
     * @param id
     * @return
     */
    private Shop queryWithPathThrough(Long id) {

        //从redis中尝试查数据
        String cachedShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(cachedShop)) {
            //命中，直接返回
            Shop shop = JSONUtil.toBean(cachedShop, Shop.class);
            return shop;
        }
        //如果命中了缓存空数据，则返回店铺信息不存在（解决缓存穿透）
        if ("".equals(cachedShop)) {
            //return Result.fail("店铺信息不存在（命中了缓存空数据）");
            return null;
        }

        //未命中，从数据库中查询数据
        Shop shop = shopMapper.queryShopById(id);
        if (shop == null) {
            //如果数据库也没有，缓存空到redis，解决缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //return Result.fail("数据不存在！");
            return null;
        }
        //将查询到的数据添加到redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
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

    /**
     * 更新商铺操作
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //先更新数据库
        shopMapper.updateShop(shop);
        //再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 分页查询
     *
     * @param current
     * @param pageSize
     * @return
     */
    @Override
    public Result queryShopByPage(Integer current, Integer pageSize, Integer typeId) {
        //计算起始索引： 起始索引=（当前页码-1）*每页现实的条数
        Integer index = (current - 1) * pageSize;

        List<Shop> shops = shopMapper.queryShopByPage(index, pageSize, typeId);
        return Result.ok(shops);
    }
}
