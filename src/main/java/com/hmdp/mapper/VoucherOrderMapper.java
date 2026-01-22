package com.hmdp.mapper;

import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 */
public interface VoucherOrderMapper {

    int createVoucherOrder(VoucherOrder voucherOrder);

}
