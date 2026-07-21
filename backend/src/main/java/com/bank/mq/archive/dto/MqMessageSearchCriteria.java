package com.bank.mq.archive.dto;

import com.bank.mq.archive.entity.MessageStatus;

public record MqMessageSearchCriteria(
		String queueName,
		MessageStatus status,
		String messageId,
		String correlationId) {
}
