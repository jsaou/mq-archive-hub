package com.bank.mq.archive.controller;

import java.time.Instant;

import com.bank.mq.archive.dto.MqMessageDto;
import com.bank.mq.archive.entity.MessageStatus;

public record MqMessageResponse(
		Long id,
		String messageId,
		String correlationId,
		String payload,
		String contentType,
		MessageStatus status,
		Instant receivedAt) {

	public static MqMessageResponse from(MqMessageDto dto) {
		return new MqMessageResponse(
				dto.id(),
				dto.messageId(),
				dto.correlationId(),
				dto.payload(),
				dto.contentType(),
				dto.status(),
				dto.receivedAt());
	}
}
