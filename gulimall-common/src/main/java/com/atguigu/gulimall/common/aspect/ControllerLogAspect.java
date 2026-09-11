package com.atguigu.gulimall.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Arrays;

/**
 * Controller 请求日志切面
 * <p>
 * 拦截所有微服务的 controller 方法，在方法执行前后打印日志：
 * <ul>
 *     <li>方法开始：{@code 类名.方法名 start, 入参: [...]}，无入参只打印 {@code 类名.方法名 start}</li>
 *     <li>方法结束：{@code 类名.方法名 end, 出参: {...}, 耗时: xxms}，无出参只打印 {@code 类名.方法名 end, 耗时: xxms}</li>
 * </ul>
 * 由于本类位于 {@code com.atguigu.gulimall.common}（业务模块默认扫不到），
 * 由 {@link com.atguigu.gulimall.common.aspect.ControllerLogAutoConfiguration} 通过 {@code AutoConfiguration.imports} 注册为 Bean 生效。
 *
 * @author liujiaxu
 */
@Aspect
@Slf4j
public class ControllerLogAspect {

    /**
     * 切点：com.atguigu 下任意层级中名为 controller 的包及其子包中的所有方法
     * （覆盖 product / coupon / member / order / ware / thirdparty 等模块）
     */
    @Pointcut("execution(* com.atguigu..controller..*(..))")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：方法执行前后打印 start / end 日志 + 耗时
     */
    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 类名 + 方法名（如 ProductController.list）
        String classAndMethod = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();

        // 1、方法开始：打印 start + 入参（无入参只打印 start）
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            log.info("{}. start, 入参: {}", classAndMethod, Arrays.toString(args));
        } else {
            log.info("{}. start", classAndMethod);
        }

        long start = System.currentTimeMillis();
        Object result = null;
        try {
            // 2、执行目标方法
            result = joinPoint.proceed();
            return result;
        } finally {
            // 3、方法结束（无论正常/异常都打印）：end + 出参（有则打印）+ 耗时
            long cost = System.currentTimeMillis() - start;
            if (result != null) {
                log.info("{}. end, 出参: {}, 耗时: {}ms", classAndMethod, result, cost);
            } else {
                log.info("{}. end, 耗时: {}ms", classAndMethod, cost);
            }
        }
    }
}
