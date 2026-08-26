package com.atguigu.gulimall.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 自定义全局过滤器：打印请求 URI 与耗时（仿照 cloud-demo/gateway 学习示例移植）
 *
 * <p>全局过滤器对所有路由自动生效，无需在 yaml 中配置；实现 {@link Ordered}
 * 控制执行优先级，数值越小越先执行。</p>
 */
@Component
public class RtGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RtGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String uri = request.getURI().toString();
        long start = System.currentTimeMillis();
        log.info("uri:{}, request:{}, response:{}", uri, request, response);
        log.info("start:{}", start);

        // 响应式编程是异步执行，doFinally 在整条链路结束后回调
        return chain.filter(exchange)
                .doFinally((result) -> {
                    long end = System.currentTimeMillis();
                    log.info("end:{}", end);
                    log.info("cost:{}", end - start);
                });
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
