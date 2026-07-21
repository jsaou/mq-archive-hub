package com.bank.mq.archive.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.repository.MqMessageRepository;
import com.bank.mq.archive.support.AbstractIntegrationTest;
import com.bank.mq.archive.support.JmsTestMessages;

import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

class MqMessageListenerIT extends AbstractIntegrationTest {

	@Autowired
	private MqMessageListener listener;

	@Autowired
	private MqMessageRepository repository;

	@BeforeEach
	void setUp() {
		repository.deleteAllInBatch();
	}

	@Test
	void onMessage_consumesAndPersistsMessage() throws Exception {
		TextMessage message = JmsTestMessages.textMessage("ID:listener-1", "CORR:1", "from-mq", "text/plain");
		Session session = mock(Session.class);

		listener.onMessage(message, session);

		assertThat(repository.findByMessageId("ID:listener-1")).hasValueSatisfying(saved -> {
			assertThat(saved.getQueueName()).isEqualTo("DEV.QUEUE.1");
			assertThat(saved.getPayload()).isEqualTo("from-mq");
			assertThat(saved.getStatus()).isEqualTo(MessageStatus.RECEIVED);
		});
	}

	@Test
	void onMessage_parksInvalidMessageOnDlqWithoutPersisting() throws Exception {
		TextMessage message = JmsTestMessages.textMessage(null, null, "poison", null);
		Session session = mock(Session.class);
		Queue dlq = mock(Queue.class);
		MessageProducer producer = mock(MessageProducer.class);
		when(session.createQueue("DEV.QUEUE.2")).thenReturn(dlq);
		when(session.createProducer(dlq)).thenReturn(producer);

		listener.onMessage(message, session);

		verify(producer).send(message);
		assertThat(repository.count()).isZero();
	}

	@Test
	void onMessage_doesNotSendToDlqForValidMessage() throws Exception {
		TextMessage message = JmsTestMessages.textMessage("ID:listener-2", null, "ok", null);
		Session session = mock(Session.class);

		listener.onMessage(message, session);

		verify(session, never()).createProducer(any());
		assertThat(repository.count()).isEqualTo(1);
	}
}
