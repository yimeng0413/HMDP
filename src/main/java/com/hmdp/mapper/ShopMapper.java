package com.hmdp.mapper;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 */
public interface ShopMapper  {

    Shop queryShopById(Long id);

    List<Shop> queryShopByPage(Integer index, Integer pageSize,Integer typeId);

    void updateShop(Shop shop);

}
