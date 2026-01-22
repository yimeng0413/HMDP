package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IVoucherService {

    Result queryVoucherOfShop(Long shopId);

    Result addSeckillVoucher(Voucher voucher);

    Result addVoucher(Voucher voucher);
}
