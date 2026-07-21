package com.bank.mq.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.exception.PermanentIngestException;
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

		ingestService.ingest(message, "DEV.QUEUE.1");

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
		ingestService.ingest(duplicate, "DEV.QUEUE.1");

		assertThat(repository.count()).isEqualTo(1);
		assertThat(repository.findByMessageId("ID:1")).hasValueSatisfying(saved ->
				assertThat(saved.getPayload()).isEqualTo("first"));
	}

	@Test
	void ingest_rejectsBlankMessageIdWithoutPersisting() throws Exception {
		TextMessage message = JmsTestMessages.textMessage(" ", null, "payload", null);

		assertThatThrownBy(() -> ingestService.ingest(message, "DEV.QUEUE.1"))
				.isInstanceOf(PermanentIngestException.class)
				.hasMessageContaining("JMSMessageID");

		assertThat(repository.count()).isZero();
	}

	@Test
	void ingest_rejectsNullPayloadWithoutPersisting() throws Exception {
		TextMessage message = JmsTestMessages.textMessage("ID:1", null, null, null);

		assertThatThrownBy(() -> ingestService.ingest(message, "DEV.QUEUE.1"))
				.isInstanceOf(PermanentIngestException.class)
				.hasMessageContaining("payload is null");

		assertThat(repository.count()).isZero();
	}

	@Test
	void ingest_rejectsUnsupportedMessageTypeWithoutPersisting() throws Exception {
		assertThatThrownBy(() -> ingestService.ingest(JmsTestMessages.bytesMessage("ID:2"), "DEV.QUEUE.1"))
				.isInstanceOf(PermanentIngestException.class)
				.hasMessageContaining("Unsupported message type");

		assertThat(repository.count()).isZero();
	}
}
