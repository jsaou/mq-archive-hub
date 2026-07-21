package com.bank.mq.archive.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;

class MqMessageDtoTest {

	@Test
	void forIngest_toEntity_mapsMqFieldsAndDefaultsStatusAndReceivedAt() {
		MqMessageDto dto = MqMessageDto.forIngest("ID:1", "CORR:1", "DEV.QUEUE.1", "payload", "text/plain");

		MqMessage entity = dto.toEntity();

		assertThat(entity.getId()).isNull();
		assertThat(entity.getMessageId()).isEqualTo("ID:1");
		assertThat(entity.getCorrelationId()).isEqualTo("CORR:1");
		assertThat(entity.getQueueName()).isEqualTo("DEV.QUEUE.1");
		assertThat(entity.getPayload()).isEqualTo("payload");
		assertThat(entity.getContentType()).isEqualTo("text/plain");
		assertThat(entity.getStatus()).isEqualTo(MessageStatus.RECEIVED);
		assertThat(entity.getReceivedAt()).isNotNull();
	}

	@Test
	void from_mapsAllFields() {
		MqMessage entity = new MqMessage("ID:1", "CORR:1", "DEV.QUEUE.1", "payload", "text/plain");

		MqMessageDto dto = MqMessageDto.from(entity);

		assertThat(dto.messageId()).isEqualTo("ID:1");
		assertThat(dto.correlationId()).isEqualTo("CORR:1");
		assertThat(dto.queueName()).isEqualTo("DEV.QUEUE.1");
		assertThat(dto.payload()).isEqualTo("payload");
		assertThat(dto.contentType()).isEqualTo("text/plain");
		assertThat(dto.status()).isEqualTo(MessageStatus.RECEIVED);
		assertThat(dto.receivedAt()).isNotNull();
	}
}
