package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 这个是统一异常处理器。捕获不是按从上到下的顺序，而是从最匹配的部分开始去匹配。
 * 比如你抛了个BusinessException 虽然也属于RuntimeException，但是他会被这里的BusinessException捕获（因为这个最精确）
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {//从这个e可以获取各种信息。这个e对象就是你throw new BusinessException()抛异常时候new出来的对象
        log.error(e.toString(), e);
        return Result.fail("业务异常："+e.toString());
    }
}
