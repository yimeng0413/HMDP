package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl implements IVoucherOrderService {

    @Autowired
    ISeckillVoucherService seckillVoucherService;

    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    VoucherOrderMapper voucherOrderMapper;

    /**
     * 购买秒杀优惠券（生成优惠券订单）
     *
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Result purchaseSeckillVoucher(Long voucherId) {

        //从数据库查，是否有对应的秒杀优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.querySeckillVoucherByVoucherId(voucherId);

        //从秒杀优惠券中获取开始和结束时间
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        //如果时间不满足要求，返回错误信息
        if (LocalDateTime.now().isBefore(beginTime)) {
            return Result.fail("秒杀还没开始！");
        }
        if (LocalDateTime.now().isAfter(endTime)) {
            return Result.fail("秒杀已经结束！");
        }
        //获取库存，如果库存不足，返回错误信息
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("优惠券库存不足！");
        }
        //库存-1
        int i = seckillVoucherService.decreaseSeckillVoucherStock(voucherId);
        if (i == 0) {
            return Result.fail("优惠券库存不足！");
        }
        //生成优惠券订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrderMapper.createVoucherOrder(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
