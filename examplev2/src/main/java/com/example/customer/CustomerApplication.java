package com.example.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

@SpringBootApplication
@EnableCaching
public class CustomerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerApplication.class, args);
	}

	//We add a redis template to the spring context to insure that all key and value data is being serialized as JSON.
	@Bean
	public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

	    RedisTemplate<Object, Object> template = new RedisTemplate<>();
	    template.setConnectionFactory(connectionFactory);
	    template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
	    template.setKeySerializer(new GenericJackson2JsonRedisSerializer());
	    template.setHashKeySerializer(new GenericJackson2JsonRedisSerializer());
	    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
	    return template;
	}

}
