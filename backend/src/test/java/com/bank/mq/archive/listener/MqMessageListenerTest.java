package com.bank.mq.archive.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import com.bank.mq.archive.config.AppProperties;
import com.bank.mq.archive.exception.PermanentIngestException;
import com.bank.mq.archive.service.MqIngestService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

@ExtendWith(MockitoExtension.class)
class MqMessageListenerTest {

	@Mock
	private MqIngestService ingestService;

	@Mock
	private TextMessage message;

	@Mock
	private Session session;

	private SimpleMeterRegistry meterRegistry;
	private MqMessageListener listener;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		AppProperties appProperties = new AppProperties(
				new AppProperties.MqProperties("DEV.QUEUE.1", "DEV.QUEUE.2", "3-10"),
				new AppProperties.ApiProperties("/api/v1", 20, 100));
		listener = new MqMessageListener(ingestService, meterRegistry, appProperties);
	}

	@Test
	void onMessage_delegatesToIngestService() throws Exception {
		listener.onMessage(message, session);

		verify(ingestService).ingest(message, "DEV.QUEUE.1");
		verify(session, never()).createProducer(any());
	}

	@Test
	void onMessage_parksPoisonMessageOnDlq() throws Exception {
		Queue dlq = mock(Queue.class);
		MessageProducer producer = mock(MessageProducer.class);
		doThrow(new PermanentIngestException("missing JMSMessageID"))
				.when(ingestService).ingest(message, "DEV.QUEUE.1");
		when(session.createQueue("DEV.QUEUE.2")).thenReturn(dlq);
		when(session.createProducer(dlq)).thenReturn(producer);

		listener.onMessage(message, session);

		verify(producer).send(message);
		assertThat(meterRegistry.counter("mq.ingest.dlq").count()).isEqualTo(1);
	}

	@Test
	void onMessage_propagatesTransientErrors() throws Exception {
		doThrow(new QueryTimeoutException("db timeout"))
				.when(ingestService).ingest(eq(message), eq("DEV.QUEUE.1"));

		assertThatThrownBy(() -> listener.onMessage(message, session))
				.isInstanceOf(QueryTimeoutException.class);

		verify(session, never()).createProducer(any());
		assertThat(meterRegistry.counter("mq.ingest.dlq").count()).isZero();
	}

	@Test
	void onMessage_propagatesDlqSendFailure() throws Exception {
		Queue dlq = mock(Queue.class);
		MessageProducer producer = mock(MessageProducer.class);
		doThrow(new PermanentIngestException("unsupported type"))
				.when(ingestService).ingest(message, "DEV.QUEUE.1");
		when(session.createQueue("DEV.QUEUE.2")).thenReturn(dlq);
		when(session.createProducer(dlq)).thenReturn(producer);
		doThrow(new JMSException("dlq unavailable")).when(producer).send(message);

		assertThatThrownBy(() -> listener.onMessage(message, session))
				.isInstanceOf(JMSException.class)
				.hasMessageContaining("dlq unavailable");

		assertThat(meterRegistry.counter("mq.ingest.dlq").count()).isZero();
	}
}
