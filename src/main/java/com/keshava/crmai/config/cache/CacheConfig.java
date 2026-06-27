package com.keshava.crmai.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "profilePermissions",
                "moduleMetadata",
                "fieldMetadata"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.SECONDS));
        return manager;
    }

    /**
     * Default key generator — all @Cacheable calls without an explicit keyGenerator
     * attribute automatically get tenant-prefixed keys (e.g. "acme:SimpleKey [uuid]").
     * This makes the JVM-level Caffeine cache logically tenant-scoped.
     */
    @Bean("keyGenerator")
    public KeyGenerator keyGenerator(@NonNull TenantAwareKeyGenerator tenantAwareKeyGenerator) {
        return tenantAwareKeyGenerator;
    }
}
