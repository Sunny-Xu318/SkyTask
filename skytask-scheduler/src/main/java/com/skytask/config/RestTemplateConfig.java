package com.skytask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * RestTemplate 配置
 * 用于 HTTP 任务执行器
 */
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        // 使用 SimpleClientHttpRequestFactory，无需额外依赖
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10 秒连接超时
        factory.setReadTimeout(300000);    // 300 秒读取超时
        factory.setBufferRequestBody(false); // 避免大请求体缓冲
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 添加 UTF-8 字符串转换器
        restTemplate.getMessageConverters().add(0, 
            new StringHttpMessageConverter(StandardCharsets.UTF_8));
        
        return restTemplate;
    }
}


