package com.bank.mq.archive.dto;

import java.time.Instant;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;

/**
 * Full message view including payload — loaded only for detail access.
 */
public record MqMessageDetailDto(
		Long id,
		String messageId,
		String correlationId,
		String payload,
		String contentType,
		MessageStatus status,
		Instant receivedAt) {

	public static MqMessageDetailDto from(MqMessage message) {
		return new MqMessageDetailDto(
				message.getId(),
				message.getMessageId(),
				message.getCorrelationId(),
				message.getPayload(),
				message.getContentType(),
				message.getStatus(),
				message.getReceivedAt());
	}
}
