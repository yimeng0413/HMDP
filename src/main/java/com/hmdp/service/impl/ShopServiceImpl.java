package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl implements IShopService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopMapper shopMapper;

    @Override
    public Result queryShopById(Long id) {
        //从redis中尝试查数据
        String cachedShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(cachedShop)) {
            //命中，直接返回
            Shop shop = JSONUtil.toBean(cachedShop, Shop.class);
            return Result.ok(shop);
        }
        //如果命中了缓存空数据，则返回店铺信息不存在（解决缓存穿透）
        if ("".equals(cachedShop)) {
            return Result.fail("店铺信息不存在（命中了缓存空数据）");
        }

        //未命中，先尝试获取锁
        Shop shop;
        try {
            //没获取到锁，等待一会，然后重新去redis缓存尝试获取数据
            if (!tryLock(LOCK_SHOP_KEY + id)) {
                Thread.sleep(50);//毫秒
                return queryShopById(id); //这个return,在这里写不写都一样  递归中，如果只是为了重复执行，不一定return 但是递归的结果想被上一层用到，那肯定得return对吧
            }
            //取到锁了，从数据库中获取数据
            shop = shopMapper.queryShopById(id);
            if (shop == null) {
                //如果数据库也没有，缓存空到redis，解决缓存穿透
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("数据不存在！");
            }
            //将查询到的数据添加到redis缓存
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY + id);
        }
        return Result.ok(shop);

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

    /**
     * 解决缓存击穿问题，用互斥锁  上锁（setNx 不存在，才能set进去）
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean isLocked = stringRedisTemplate.opsForValue().setIfAbsent(key, "", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isLocked);
    }

    /**
     * 解决缓存击穿问题，互斥锁  释放锁（删除对应key）
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
