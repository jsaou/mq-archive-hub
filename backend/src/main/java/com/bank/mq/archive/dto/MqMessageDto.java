package com.bank.mq.archive.dto;

import java.time.Instant;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;

public record MqMessageDto(
		Long id,
		String messageId,
		String correlationId,
		String queueName,
		String payload,
		String contentType,
		MessageStatus status,
		Instant receivedAt) {

	public static MqMessageDto forIngest(
			String messageId,
			String correlationId,
			String queueName,
			String payload,
			String contentType) {
		return new MqMessageDto(
				null, messageId, correlationId, queueName, payload, contentType, MessageStatus.RECEIVED, null);
	}

	public static MqMessageDto forFailure(
			String messageId,
			String correlationId,
			String queueName,
			String payload,
			String contentType,
			MessageStatus status) {
		return new MqMessageDto(null, messageId, correlationId, queueName, payload, contentType, status, null);
	}

	public static MqMessageDto from(MqMessage message) {
		return new MqMessageDto(
				message.getId(),
				message.getMessageId(),
				message.getCorrelationId(),
				message.getQueueName(),
				message.getPayload(),
				message.getContentType(),
				message.getStatus(),
				message.getReceivedAt());
	}

	public MqMessage toEntity() {
		return new MqMessage(messageId, correlationId, queueName, payload, contentType, status);
	}
}
