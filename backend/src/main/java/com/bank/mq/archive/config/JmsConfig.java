package com.bank.mq.archive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import jakarta.jms.ConnectionFactory;

@Configuration
@EnableJms
public class JmsConfig {

	@Bean
	DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
			ConnectionFactory connectionFactory,
			JmsErrorHandler errorHandler,
			AppProperties appProperties,
			@Value("${spring.jms.listener.auto-startup:true}") boolean autoStartup) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setSessionTransacted(true);
		factory.setConcurrency(appProperties.mq().concurrency());
		factory.setErrorHandler(errorHandler);
		factory.setAutoStartup(autoStartup);
		return factory;
	}
}
