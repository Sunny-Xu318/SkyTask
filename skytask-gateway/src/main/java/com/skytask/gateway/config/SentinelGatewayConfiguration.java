package com.skytask.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SentinelGatewayConfiguration {

    private static final String TENANT_HEADER = "X-SkyTask-Tenant";

    @PostConstruct
    public void init() {
        try {
            initCustomizedApis();
            initGatewayRules();
            GatewayCallbackManager.setBlockHandler((exchange, t) -> {
                String body = "{\"code\":429,\"message\":\"Too many requests for tenant\"}";
                return org.springframework.web.reactive.function.server.ServerResponse.status(429)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .bodyValue(body);
            });
            GatewayCallbackManager.setRequestOriginParser(exchange ->
                    exchange.getRequest().getHeaders().getFirst(TENANT_HEADER));
        } catch (Exception ex) {
            log.warn("Failed to init Sentinel gateway rules: {}", ex.getMessage());
        }
    }

    private void initCustomizedApis() {
        Set<ApiDefinition> definitions = new HashSet<>();
        definitions.add(new ApiDefinition("admin-api")
                .setPredicateItems(Collections.singleton(new ApiPathPredicateItem()
                        .setPattern("/api/**"))));
        definitions.add(new ApiDefinition("scheduler-api")
                .setPredicateItems(Collections.singleton(new ApiPathPredicateItem()
                        .setPattern("/scheduler/**"))));
        definitions.add(new ApiDefinition("worker-api")
                .setPredicateItems(Collections.singleton(new ApiPathPredicateItem()
                        .setPattern("/worker/**"))));
        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule("admin-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(60)
                .setIntervalSec(1)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                        .setFieldName(TENANT_HEADER)));
        rules.add(new GatewayFlowRule("scheduler-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(120)
                .setIntervalSec(1)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                        .setFieldName(TENANT_HEADER)));
        GatewayRuleManager.loadRules(rules);
    }
}
