package com.bank.mq.archive.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.bank.mq.archive.dto.MqMessageSearchCriteria;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;

@DataJpaTest
class MqMessageRepositoryTest {

	private static final Instant OLDER = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant NEWER = Instant.parse("2026-01-01T11:00:00Z");

	@Autowired
	private MqMessageRepository repository;

	@Test
	void save_andFindByMessageId() {
		MqMessage message = new MqMessage("ID:1", "CORR:1", "DEV.QUEUE.1", "payload", "text/plain");

		MqMessage saved = repository.saveAndFlush(message);

		assertThat(saved.getId()).isNotNull();
		assertThat(repository.findByMessageId("ID:1")).hasValueSatisfying(found -> {
			assertThat(found.getQueueName()).isEqualTo("DEV.QUEUE.1");
			assertThat(found.getPayload()).isEqualTo("payload");
			assertThat(found.getCorrelationId()).isEqualTo("CORR:1");
			assertThat(found.getContentType()).isEqualTo("text/plain");
			assertThat(found.getStatus()).isEqualTo(MessageStatus.RECEIVED);
			assertThat(found.getReceivedAt()).isNotNull();
		});
	}

	@Test
	void existsByMessageId_returnsExpectedValue() {
		repository.saveAndFlush(new MqMessage("ID:1", null, "DEV.QUEUE.1", "payload", null));

		assertThat(repository.existsByMessageId("ID:1")).isTrue();
		assertThat(repository.existsByMessageId("ID:missing")).isFalse();
	}

	@Test
	void save_rejectsDuplicateMessageId() {
		repository.saveAndFlush(new MqMessage("ID:1", null, "DEV.QUEUE.1", "first", null));

		assertThatThrownBy(() ->
				repository.saveAndFlush(new MqMessage("ID:1", null, "DEV.QUEUE.1", "second", null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findAll_filtersByQueueName() {
		repository.saveAndFlush(new MqMessage("ID:1", null, "Q.A", "a", null));
		repository.saveAndFlush(new MqMessage("ID:2", null, "Q.B", "b", null));

		Page<MqMessage> page = repository.findAll(
				MqMessageSpecs.withFilters(new MqMessageSearchCriteria("Q.A", null, null, null)),
				PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().getFirst().getMessageId()).isEqualTo("ID:1");
	}

	@Test
	void findAll_filtersByStatus() {
		MqMessage received = repository.saveAndFlush(new MqMessage("ID:1", null, "Q.A", "a", null));
		MqMessage processed = new MqMessage("ID:2", null, "Q.A", "b", null);
		processed.markProcessed();
		repository.saveAndFlush(processed);

		Page<MqMessage> page = repository.findAll(
				MqMessageSpecs.withFilters(new MqMessageSearchCriteria(null, MessageStatus.PROCESSED, null, null)),
				PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().getFirst().getMessageId()).isEqualTo("ID:2");
		assertThat(repository.findById(received.getId())).isPresent();
	}

	@Test
	void findAll_filtersByMessageIdAndCorrelationId() {
		repository.saveAndFlush(new MqMessage("ID:1", "CORR:1", "Q.A", "a", null));
		repository.saveAndFlush(new MqMessage("ID:2", "CORR:2", "Q.A", "b", null));

		Page<MqMessage> page = repository.findAll(
				MqMessageSpecs.withFilters(new MqMessageSearchCriteria(null, null, "ID:1", "CORR:1")),
				PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().getFirst().getPayload()).isEqualTo("a");
	}

	@Test
	void findAll_paginatesAndSortsByReceivedAtDesc() {
		repository.saveAndFlush(messageAt("ID:old", "old", OLDER));
		repository.saveAndFlush(messageAt("ID:new", "new", NEWER));

		Page<MqMessage> page = repository.findAll(
				MqMessageSpecs.withFilters(null),
				PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "receivedAt")));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().getFirst().getMessageId()).isEqualTo("ID:new");
	}

	private static MqMessage messageAt(String messageId, String payload, Instant receivedAt) {
		MqMessage message = new MqMessage(messageId, null, "Q.A", payload, null);
		ReflectionTestUtils.setField(message, "receivedAt", receivedAt);
		return message;
	}
}
