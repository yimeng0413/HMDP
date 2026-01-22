package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class VoucherServiceImpl implements IVoucherService {

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        //List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        List<Voucher> vouchers = voucherMapper.queryVoucherByShopId(shopId);

        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 新增秒杀优惠券
     * @param voucher
     */
    @Override
    @Transactional
    public Result addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        voucherMapper.addVoucher(voucher);
        //save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        //seckillVoucherService.save(seckillVoucher);
        seckillVoucherMapper.addSeckillVoucher(seckillVoucher);
        return Result.ok();
    }

    /**
     * 新增普通优惠券
     * @param voucher
     */
    @Override
    public Result addVoucher(Voucher voucher) {
        voucherMapper.addVoucher(voucher);
        return Result.ok();
    }
}
