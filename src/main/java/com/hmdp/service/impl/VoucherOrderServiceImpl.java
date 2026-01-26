package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
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

        //一人一单前，先加锁（不然高并发情况下一人也可能下单多个优惠券）
        Long userId = UserHolder.getUser().getId();
        //这里toString()不行。因为synchronized锁的是对象引用，toString()每次都是new一个string，地址都不同 所以用intern()
        //intern()方法是去String常量池去找，如果有，返回池子里的字符串的引用；没有的话，把这个String放进常量池，同时返回池子里的引用。
        //这里用了悲观锁：因为这个不是更新，是做了存在check然后insert，不方便用乐观锁
        //（乐观锁核心是检查数据是否发生了变化，你新增一条数据怎么去判断数据发生了变化之类的呀，所以用悲观锁）
        synchronized (userId.toString().intern()){
            //这里为啥要用代理对象？ 因为Spring的事务是通过AOP代理实现的。只有经过代理对象调用的方法，事务才会生效。
            //所以这一步实际做的事情就是：拿到当前线程里的代理对象。只有这个对象调用的方法，才能被AOP拦截到
            //普通我们是在service某个方法上写@Transactional，然后Controller层直接调service的这个方法，这个时候事务是生效的，为啥？
            //因为注入到Controller层的不是service对象，而是这个的代理对象！！ 通过这个代理对象去调用了service的具体方法，所以事务生效了！
            //核心一句话：Spring事务是否生效，取决于：有没有通过代理对象去调用，事务传播行为只有在走代理的情况下才生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单：检查是否下过单，如果下过单，则不允许重复下单
        Long userId = UserHolder.getUser().getId();
        Integer existedVoucherOrderNumber = voucherOrderMapper.queryVoucherOrderByUserId(userId, voucherId);
        //如果数据库中已经存在数据（之前下过单），则不允许重复下单
        if (existedVoucherOrderNumber != null) {
            return Result.fail("不允许重复下单！");
        }
        //库存-1
        int updatedRows = seckillVoucherService.decreaseSeckillVoucherStock(voucherId);
        if (updatedRows == 0) {
            throw new BusinessException("库存超卖！不允许执行");
        }
        //生成优惠券订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrderMapper.createVoucherOrder(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
