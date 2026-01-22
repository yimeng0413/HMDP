package com.hmdp.mapper;

import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 */
public interface SeckillVoucherMapper {

    void addSeckillVoucher(SeckillVoucher voucher);

    int decreaseSeckillVoucherStock(Long voucherId);

    SeckillVoucher querySeckillVoucherByVoucherId(Long voucherId);

}
