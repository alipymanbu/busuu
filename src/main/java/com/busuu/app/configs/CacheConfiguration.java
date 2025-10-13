package com.busuu.app.configs;

import com.busuu.app.entities.enums.CacheKey;
import com.busuu.app.entities.Chapter;
import com.busuu.app.entities.Language;
import com.busuu.app.entities.Lesson;
import com.busuu.app.entities.Level;
import com.busuu.app.entities.Role;
import com.busuu.app.entities.Word;
import lombok.RequiredArgsConstructor;
import org.hibernate.cache.jcache.ConfigSettings;
import org.redisson.api.RedissonClient;
import org.redisson.jcache.configuration.RedissonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfiguration {

    private GitProperties gitProperties;
    private BuildProperties buildProperties;

    private final RedissonClient redissonClient;

    @Value("${cache.redis.expiration}")
    private Long expiration;

    @Bean
    public javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration() {
        MutableConfiguration<Object, Object> jcacheConfig = new MutableConfiguration<>();
        jcacheConfig.setStatisticsEnabled(true);
        jcacheConfig.setExpiryPolicyFactory(
                CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, expiration))
        );
        return RedissonConfiguration.fromInstance(redissonClient, jcacheConfig);
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(javax.cache.CacheManager cm) {
        return hibernateProperties -> hibernateProperties.put(ConfigSettings.CACHE_MANAGER, cm);
    }

    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer(javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration) {
        return cm -> {
            // Hibernate regions
            createCache(cm, "default-update-timestamps-region", jcacheConfiguration);
            createCache(cm, "default-query-results-region", jcacheConfiguration);

            // Cache Entities
//            createCache(cm, Level.class.getName(), jcacheConfiguration);
//            createCache(cm, Role.class.getName(), jcacheConfiguration);
//            createCache(cm, Lesson.class.getName(), jcacheConfiguration);
//            createCache(cm, Word.class.getName(), jcacheConfiguration);
//            createCache(cm, Chapter.class.getName(), jcacheConfiguration);
//            createCache(cm, Language.class.getName(), jcacheConfiguration);

            // Cache Queries
            createCache(cm, "word-cache", jcacheConfiguration);

            // Service Cache
            createCache(cm, CacheKey.USER_PRINCIPAL.getName(), jcacheConfiguration);
        };
    }

    private void createCache(javax.cache.CacheManager cm,
                             String cacheName,
                             javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration) {
        javax.cache.Cache<Object, Object> cache = cm.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        } else {
            cm.createCache(cacheName, jcacheConfiguration);
        }
    }

    @Autowired(required = false)
    public void setGitProperties(GitProperties gitProperties) {
        this.gitProperties = gitProperties;
    }

    @Autowired(required = false)
    public void setBuildProperties(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return new PrefixedKeyGenerator(this.gitProperties, this.buildProperties);
    }

}
