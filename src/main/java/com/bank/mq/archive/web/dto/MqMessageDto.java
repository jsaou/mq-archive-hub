package com.bank.mq.archive.web.dto;

import com.bank.mq.archive.domain.MqMessage;

public record MqMessageDto(
		String messageId,
		String correlationId,
		String queueName,
		String payload,
		String contentType) {

	public MqMessage toEntity() {
		return new MqMessage(messageId, correlationId, queueName, payload, contentType);
	}
}
