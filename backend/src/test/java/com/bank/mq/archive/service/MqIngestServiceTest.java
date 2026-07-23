package com.bank.mq.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;
import com.bank.mq.archive.repository.MqMessageRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.jms.BytesMessage;
import jakarta.jms.TextMessage;

@ExtendWith(MockitoExtension.class)
class MqIngestServiceTest {

	@Mock
	private MqMessageRepository repository;

	@Mock
	private TextMessage textMessage;

	private MeterRegistry meterRegistry;
	private MqIngestService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new MqIngestService(repository, meterRegistry);
	}

	@Test
	void ingest_savesNewMessage() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getJMSCorrelationID()).thenReturn("CORR:1");
		when(textMessage.getText()).thenReturn("payload");
		when(textMessage.getStringProperty("JMS_IBM_Format")).thenReturn(null);
		when(textMessage.getJMSType()).thenReturn("text/plain");
		when(repository.existsByMessageId("ID:1")).thenReturn(false);

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.SUCCESS);

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());

		MqMessage saved = captor.getValue();
		assertThat(saved.getMessageId()).isEqualTo("ID:1");
		assertThat(saved.getCorrelationId()).isEqualTo("CORR:1");
		assertThat(saved.getQueueName()).isEqualTo("DEV.QUEUE.1");
		assertThat(saved.getPayload()).isEqualTo("payload");
		assertThat(saved.getContentType()).isEqualTo("text/plain");
		assertThat(saved.getStatus()).isEqualTo(MessageStatus.RECEIVED);
		assertThat(saved.getReceivedAt()).isNotNull();
		assertThat(meterRegistry.counter("mq.ingest.success").count()).isEqualTo(1);
	}

	@Test
	void ingest_usesIbmFormatAsContentType() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getText()).thenReturn("payload");
		when(textMessage.getStringProperty("JMS_IBM_Format")).thenReturn("MQSTR");
		when(repository.existsByMessageId("ID:1")).thenReturn(false);

		service.ingest(textMessage, "DEV.QUEUE.1");

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getContentType()).isEqualTo("MQSTR");
	}

	@Test
	void ingest_acceptsMissingOptionalFields() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getJMSCorrelationID()).thenReturn(null);
		when(textMessage.getText()).thenReturn("payload");
		when(textMessage.getStringProperty("JMS_IBM_Format")).thenReturn(null);
		when(textMessage.getJMSType()).thenReturn(null);
		when(repository.existsByMessageId("ID:1")).thenReturn(false);

		service.ingest(textMessage, "DEV.QUEUE.1");

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getCorrelationId()).isNull();
		assertThat(captor.getValue().getContentType()).isNull();
		assertThat(captor.getValue().getReceivedAt()).isNotNull();
		assertThat(meterRegistry.counter("mq.ingest.success").count()).isEqualTo(1);
	}

	@Test
	void ingest_skipsDuplicate() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(repository.existsByMessageId("ID:1")).thenReturn(true);
		when(repository.findByMessageId("ID:1")).thenReturn(Optional.of(
				new MqMessage("ID:1", null, "DEV.QUEUE.1", "payload", null)));

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DUPLICATE);

		verify(repository, never()).save(any());
		assertThat(meterRegistry.counter("mq.ingest.duplicate").count()).isEqualTo(1);
	}

	@Test
	void ingest_asksRedeliveryToParkWhenDuplicateIsAlreadyDlq() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(repository.existsByMessageId("ID:1")).thenReturn(true);
		MqMessage existing = new MqMessage("ID:1", null, "DEV.QUEUE.1", "[ingest-dlq] boom", null, MessageStatus.DLQ);
		when(repository.findByMessageId("ID:1")).thenReturn(Optional.of(existing));

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);
		verify(repository, never()).save(any());
	}

	@Test
	void ingest_skipsDuplicateOnConstraintViolation() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getText()).thenReturn("payload");
		when(repository.existsByMessageId("ID:1")).thenReturn(false);
		when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DUPLICATE);

		assertThat(meterRegistry.counter("mq.ingest.duplicate").count()).isEqualTo(1);
		assertThat(meterRegistry.counter("mq.ingest.success").count()).isZero();
	}

	@Test
	void ingest_archivesMissingMessageIdAsDlq() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn(" ");
		when(textMessage.getJMSCorrelationID()).thenReturn(null);

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		MqMessage saved = captor.getValue();
		assertThat(saved.getMessageId()).startsWith("MISSING:");
		assertThat(saved.getStatus()).isEqualTo(MessageStatus.DLQ);
		assertThat(saved.getPayload()).contains("missing JMSMessageID");
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isEqualTo(1);
	}

	@Test
	void ingest_archivesNullPayloadAsError() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getJMSCorrelationID()).thenReturn("CORR:1");
		when(textMessage.getText()).thenReturn(null);
		when(textMessage.getStringProperty("JMS_IBM_Format")).thenReturn(null);
		when(textMessage.getJMSType()).thenReturn("text/plain");
		when(repository.existsByMessageId("ID:1")).thenReturn(false);

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.ERROR);

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		MqMessage saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(MessageStatus.ERROR);
		assertThat(saved.getPayload()).contains("payload is null");
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isEqualTo(1);
		assertThat(meterRegistry.counter("mq.ingest.success").count()).isZero();
	}

	@Test
	void ingest_archivesUnsupportedTypeAsDlq() throws Exception {
		BytesMessage bytesMessage = mock(BytesMessage.class);
		when(bytesMessage.getJMSMessageID()).thenReturn("ID:2");
		when(bytesMessage.getJMSCorrelationID()).thenReturn(null);
		when(repository.existsByMessageId("ID:2")).thenReturn(false);

		assertThat(service.ingest(bytesMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		MqMessage saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(MessageStatus.DLQ);
		assertThat(saved.getPayload()).contains("Unsupported message type");
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isEqualTo(1);
	}

	@Test
	void ingest_propagatesTransientDataAccessErrors() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getText()).thenReturn("payload");
		when(repository.existsByMessageId("ID:1")).thenReturn(false);
		when(repository.save(any())).thenThrow(new QueryTimeoutException("db timeout"));

		assertThatThrownBy(() -> service.ingest(textMessage, "DEV.QUEUE.1"))
				.isInstanceOf(QueryTimeoutException.class);

		assertThat(meterRegistry.counter("mq.ingest.success").count()).isZero();
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isZero();
	}
}
