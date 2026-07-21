package com.bank.mq.archive.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

@Component
public class JmsErrorHandler implements ErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(JmsErrorHandler.class);

	@Override
	public void handleError(Throwable t) {
		log.error("JMS listener failed: {}", t.getMessage(), t);
	}
}
