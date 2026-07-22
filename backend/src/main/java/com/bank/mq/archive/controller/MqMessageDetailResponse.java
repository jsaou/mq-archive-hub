package com.bank.mq.archive.controller;

import java.time.Instant;

import com.bank.mq.archive.dto.MqMessageDetailDto;
import com.bank.mq.archive.entity.MessageStatus;

public record MqMessageDetailResponse(
		Long id,
		String messageId,
		String correlationId,
		String payload,
		String contentType,
		MessageStatus status,
		Instant receivedAt) {

	public static MqMessageDetailResponse from(MqMessageDetailDto dto) {
		return new MqMessageDetailResponse(
				dto.id(),
				dto.messageId(),
				dto.correlationId(),
				dto.payload(),
				dto.contentType(),
				dto.status(),
				dto.receivedAt());
	}
}
