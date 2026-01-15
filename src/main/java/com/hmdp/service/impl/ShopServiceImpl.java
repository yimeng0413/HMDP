package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        if("".equals(cachedShop)){
            return Result.fail("店铺信息不存在（命中了缓存空数据）");
        }

        //未命中，从数据库中查询数据
        Shop shop = shopMapper.queryShopById(id);
        if (shop == null) {
            //如果数据库也没有，缓存空到redis，解决缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("数据不存在！");
        }
        //将查询到的数据添加到redis缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);


    }

    /**
     * 更新商铺操作
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if(id == null){
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
