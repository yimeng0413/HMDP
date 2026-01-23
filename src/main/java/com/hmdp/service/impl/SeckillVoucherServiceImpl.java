package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 */
@Service
public class SeckillVoucherServiceImpl implements ISeckillVoucherService {

    @Autowired
    SeckillVoucherMapper seckillVoucherMapper;

    @Override
    public SeckillVoucher querySeckillVoucherByVoucherId(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherMapper.querySeckillVoucherByVoucherId(voucherId);
        if(seckillVoucher==null){
            throw new BusinessException("秒杀券库存不足！");
        }
        return seckillVoucher;
    }

    @Override
    public int decreaseSeckillVoucherStock(Long voucherId) {
        int updatedRows = seckillVoucherMapper.decreaseSeckillVoucherStock(voucherId);
        return updatedRows;
    }
}
