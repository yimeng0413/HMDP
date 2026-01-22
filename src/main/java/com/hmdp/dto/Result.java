package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 这个是统一返回的包装类
 *
 * 细心的你一定发现了 这里是不包括code的
 * 这里使用的是success和errorMsg 当然前端主要是通过这个success来判断是否OK的
 * 大型的项目一般还会有状态码code 一般也会封装在Result中去使用
 * 异常的时候，会new一个自定义的异常比如new BusinessException（4001，“店铺不存在”）
 * 然后在WebExceptionAdvice捕获异常之后，通过e获取各种信息 然后封装到这个统一包装类返回即可
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static Result ok(){
        return new Result(true, null, null, null);
    }
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
