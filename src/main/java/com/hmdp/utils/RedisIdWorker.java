package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;

@Component
public class RedisIdWorker {

    //2025/1/1 0:0:0对应的second
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    //时间戳需要左移的位数
    private static final int COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 利用时间戳+序列号的形式，生成全局唯一ID
     *
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix) {
        //时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //从redis生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //这里不会null 如果key在redis不存在，他会新建一个那个key
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接并返回
        //这里你看timestamp本身就是long，理论上他就是64位的 但！！ 实际用不满64位，32位都用不满 所以这里就放心大胆左移了32位。
        //这个序列号也一样，他是long，但是用不满64，32位都用不满 所以就直接拼上了timestamp
        //这个时间戳单位是秒，1秒内生成的ID 他的前32位是一模一样的，所以还拼了个序列号（后32位）
        //当一秒内序列号超过了2的32次方的时候，也就是一秒内生成了数十亿个ID才会出错（基本不可能 淘宝最高才几十万（双十一几年前数据）平时就几千--老师说的）
        //结论就是：当时间戳32个bit不够表示（距离2025/1/1几百年后），或者序列号32个bit不够表示（几十亿）的时候，这个方案才会出问题，否则是OK的
        return timestamp << COUNT_BITS | count;

    }


    /**
     * 生成2025/1/1 0:0:0 时间对应的second（long型）
     *
     * @param args
     */
    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        long second = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);

    }

}
