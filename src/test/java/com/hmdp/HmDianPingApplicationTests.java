package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    ShopServiceImpl shopService;

    @Autowired
    CacheClient cacheClient;

    @Autowired
    ShopMapper shopMapper;

    @Test
    void saveShop(){
        shopService.saveShopDataToRedis(1L,10L);
    }

    @Test
    void saveData(){
        Shop shop = shopMapper.queryShopById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L, shop,10L, TimeUnit.SECONDS);
    }

}
