package com.bank.mq.archive.dto;

import java.time.Instant;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;

/**
 * List/search projection without payload — keeps high-volume list responses light.
 *
 * <p>Constructor argument order must stay aligned with
 * {@code MqMessageRepositoryImpl} {@code CriteriaBuilder.construct(...)} selection.
 */
public record MqMessageSummaryDto(
		Long id,
		String messageId,
		String correlationId,
		String contentType,
		MessageStatus status,
		Instant receivedAt) {

	public static MqMessageSummaryDto from(MqMessage message) {
		return new MqMessageSummaryDto(
				message.getId(),
				message.getMessageId(),
				message.getCorrelationId(),
				message.getContentType(),
				message.getStatus(),
				message.getReceivedAt());
	}
}
