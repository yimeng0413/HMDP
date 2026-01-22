package com.hmdp.exception;

public class BusinessException extends RuntimeException {

    //private final Integer code;

    public BusinessException(String message){
        //这里这个super，推荐使用。当然可以自己维护一个message成员变量然后给他赋值，但是这样就会废掉Java异常体系自带的message机制
        //日志打印log.error("xx",e),堆栈输出，AOP捕获等等都依赖原生的getMessage()。因此如果你自己维护message变量，那么这里都会是null
        //正常思路应该是：message属于异常体系的一部分 所以super(message) code属于业务扩展字段，所以this.code = code这样手动去赋值即可
        super(message);
        //this.code = code;
    }
}
