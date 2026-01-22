package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Autowired
    VoucherOrderServiceImpl voucherOrderService;

    /**
     * 购买秒杀优惠券(创建优惠券订单）
     * @param voucherId
     * @return
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        //这个seckill  是在中国习惯的叫法  second+kill 秒杀的意思  在国外可以叫flashSale

        Result result = voucherOrderService.purchaseSeckillVoucher(voucherId);
        return result;
    }
}
