package com.bank.mq.archive.controller;

import java.time.Instant;

import com.bank.mq.archive.dto.MqMessageSummaryDto;
import com.bank.mq.archive.entity.MessageStatus;

public record MqMessageSummaryResponse(
		Long id,
		String messageId,
		String correlationId,
		String contentType,
		MessageStatus status,
		Instant receivedAt) {

	public static MqMessageSummaryResponse from(MqMessageSummaryDto dto) {
		return new MqMessageSummaryResponse(
				dto.id(),
				dto.messageId(),
				dto.correlationId(),
				dto.contentType(),
				dto.status(),
				dto.receivedAt());
	}
}
