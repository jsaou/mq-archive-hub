package com.bank.mq.archive.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.bank.mq.archive.domain.MessageStatus;
import com.bank.mq.archive.domain.MqMessage;

@DataJpaTest
class MqMessageRepositoryTest {

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
}
