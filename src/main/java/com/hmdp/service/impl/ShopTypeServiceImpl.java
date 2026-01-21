package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl implements IShopTypeService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopTypeMapper shopTypeMapper;

    @Override
    public Result queryTypeList() {

        //尝试从redis中取出数据 (List)
        List<String> objs = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
        //这种从redis取的，如果没命中，都是null
        if (!objs.isEmpty()) {
            //命中，直接将数据返回给前端
            List<ShopType> shopTypes = objs.stream().map(s -> JSONUtil.toBean(s, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        //如果没命中，去数据库查
        List<ShopType> shopTypes = shopTypeMapper.queryShopTypeAll();
        if (shopTypes.isEmpty()) {
            return Result.fail("商店类型信息不存在");
        }
        //将查到的数据从bean转换成string，以便存入redis（List)
        List<String> cachedShopTypes = shopTypes.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        //存入redis
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOPTYPE_KEY, cachedShopTypes);
        //设置过期时间
        stringRedisTemplate.expire(CACHE_SHOPTYPE_KEY, CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypes);
    }
}
