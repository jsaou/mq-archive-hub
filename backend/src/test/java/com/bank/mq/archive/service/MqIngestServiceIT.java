package com.bank.mq.archive.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.repository.MqMessageRepository;
import com.bank.mq.archive.support.AbstractIntegrationTest;
import com.bank.mq.archive.support.JmsTestMessages;

import jakarta.jms.TextMessage;

class MqIngestServiceIT extends AbstractIntegrationTest {

	@Autowired
	private MqIngestService ingestService;

	@Autowired
	private MqMessageRepository repository;

	@BeforeEach
	void setUp() {
		repository.deleteAllInBatch();
	}

	@Test
	void ingest_persistsValidTextMessage() throws Exception {
		TextMessage message = JmsTestMessages.textMessage("ID:1", "CORR:1", "hello", "text/plain");

		assertThat(ingestService.ingest(message, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.SUCCESS);

		assertThat(repository.findByMessageId("ID:1")).hasValueSatisfying(saved -> {
			assertThat(saved.getCorrelationId()).isEqualTo("CORR:1");
			assertThat(saved.getQueueName()).isEqualTo("DEV.QUEUE.1");
			assertThat(saved.getPayload()).isEqualTo("hello");
			assertThat(saved.getContentType()).isEqualTo("text/plain");
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.RECEIVED);
		});
	}

	@Test
	void ingest_skipsDuplicateWithoutCreatingSecondRow() throws Exception {
		TextMessage first = JmsTestMessages.textMessage("ID:1", null, "first", null);
		TextMessage duplicate = JmsTestMessages.textMessage("ID:1", null, "second", null);

		ingestService.ingest(first, "DEV.QUEUE.1");
		assertThat(ingestService.ingest(duplicate, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DUPLICATE);

		assertThat(repository.count()).isEqualTo(1);
		assertThat(repository.findByMessageId("ID:1")).hasValueSatisfying(saved ->
				assertThat(saved.getPayload()).isEqualTo("first"));
	}

	@Test
	void ingest_archivesBlankMessageIdAsDlq() throws Exception {
		TextMessage message = JmsTestMessages.textMessage(" ", null, "payload", null);

		assertThat(ingestService.ingest(message, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		assertThat(repository.count()).isEqualTo(1);
		assertThat(repository.findAll().getFirst()).satisfies(saved -> {
			assertThat(saved.getMessageId()).startsWith("MISSING:");
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.DLQ);
			assertThat(saved.getPayload()).contains("missing JMSMessageID");
		});
	}

	@Test
	void ingest_archivesNullPayloadAsError() throws Exception {
		TextMessage message = JmsTestMessages.textMessage("ID:1", null, null, null);

		assertThat(ingestService.ingest(message, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.ERROR);

		assertThat(repository.findByMessageId("ID:1")).hasValueSatisfying(saved -> {
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.ERROR);
			assertThat(saved.getPayload()).contains("payload is null");
		});
	}

	@Test
	void ingest_archivesUnsupportedMessageTypeAsDlq() throws Exception {
		assertThat(ingestService.ingest(JmsTestMessages.bytesMessage("ID:2"), "DEV.QUEUE.1"))
				.isEqualTo(IngestOutcome.DLQ);

		assertThat(repository.findByMessageId("ID:2")).hasValueSatisfying(saved -> {
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.DLQ);
			assertThat(saved.getPayload()).contains("Unsupported message type");
		});
	}

	@Test
	void ingest_archivesOversizedPayloadAsDlq() throws Exception {
		TextMessage message = JmsTestMessages.textMessage(
				"ID:big", null, "x".repeat(1_048_576 + 1), null);

		assertThat(ingestService.ingest(message, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		assertThat(repository.findByMessageId("ID:big")).hasValueSatisfying(saved -> {
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.DLQ);
			assertThat(saved.getPayload()).contains("Payload exceeds max size");
		});
	}

	@Test
	void ingest_archivesWhenRedeliveryExceededAsDlq() throws Exception {
		TextMessage message = JmsTestMessages.textMessage("ID:retry", null, "payload", null);
		org.mockito.Mockito.when(message.propertyExists("JMSXDeliveryCount")).thenReturn(true);
		org.mockito.Mockito.when(message.getIntProperty("JMSXDeliveryCount")).thenReturn(6);

		assertThat(ingestService.ingest(message, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		assertThat(repository.findByMessageId("ID:retry")).hasValueSatisfying(saved -> {
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.DLQ);
			assertThat(saved.getPayload()).contains("exceeded max redelivery");
		});
	}
}
