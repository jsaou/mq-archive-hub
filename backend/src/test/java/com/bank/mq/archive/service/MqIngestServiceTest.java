package com.bank.mq.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.bank.mq.archive.config.AppProperties;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;
import com.bank.mq.archive.repository.MqMessageRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.jms.BytesMessage;
import jakarta.jms.TextMessage;

@ExtendWith(MockitoExtension.class)
class MqIngestServiceTest {

	private static final int MAX_REDELIVERY = 5;
	private static final int MAX_PAYLOAD_BYTES = 1024;

	@Mock
	private MqMessageRepository repository;

	@Mock
	private TextMessage textMessage;

	@Mock
	private PlatformTransactionManager transactionManager;

	private MeterRegistry meterRegistry;
	private MqIngestService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		AppProperties appProperties = new AppProperties(
				new AppProperties.MqProperties(
						"DEV.QUEUE.1", "DEV.QUEUE.2", "3-10", MAX_REDELIVERY, MAX_PAYLOAD_BYTES),
				new AppProperties.ApiProperties("/api/v1", 100, 20));
		lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
				.thenReturn(new SimpleTransactionStatus());
		service = new MqIngestService(repository, meterRegistry, appProperties, transactionManager);
	}

	@Test
	void ingest_savesNewMessage() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getJMSCorrelationID()).thenReturn("CORR:1");
		when(textMessage.getText()).thenReturn("payload");
		when(textMessage.getStringProperty("JMS_IBM_Format")).thenReturn(null);
		when(textMessage.getJMSType()).thenReturn("text/plain");
		stubFirstDelivery();

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
		stubFirstDelivery();

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
		stubFirstDelivery();

		service.ingest(textMessage, "DEV.QUEUE.1");

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getCorrelationId()).isNull();
		assertThat(captor.getValue().getContentType()).isNull();
		assertThat(captor.getValue().getReceivedAt()).isNotNull();
		assertThat(meterRegistry.counter("mq.ingest.success").count()).isEqualTo(1);
	}

	@Test
	void ingest_skipsDuplicateOnConstraintViolation() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getText()).thenReturn("payload");
		stubFirstDelivery();
		when(repository.save(any())).thenThrow(uniqueMessageIdViolation());
		when(repository.findByMessageId("ID:1")).thenReturn(Optional.of(
				new MqMessage("ID:1", null, "DEV.QUEUE.1", "payload", null)));

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DUPLICATE);

		assertThat(meterRegistry.counter("mq.ingest.duplicate").count()).isEqualTo(1);
		assertThat(meterRegistry.counter("mq.ingest.success").count()).isZero();
	}

	@Test
	void ingest_asksRedeliveryToParkWhenDuplicateIsAlreadyDlq() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getText()).thenReturn("payload");
		stubFirstDelivery();
		when(repository.save(any())).thenThrow(uniqueMessageIdViolation());
		MqMessage existing = new MqMessage("ID:1", null, "DEV.QUEUE.1", "[ingest-dlq] boom", null, MessageStatus.DLQ);
		when(repository.findByMessageId("ID:1")).thenReturn(Optional.of(existing));

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);
	}

	@Test
	void ingest_propagatesNonUniqueIntegrityViolations() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getText()).thenReturn("payload");
		stubFirstDelivery();
		DataIntegrityViolationException other = new DataIntegrityViolationException(
				"null value in column \"payload\"");
		when(repository.save(any())).thenThrow(other);

		assertThatThrownBy(() -> service.ingest(textMessage, "DEV.QUEUE.1"))
				.isSameAs(other);

		assertThat(meterRegistry.counter("mq.ingest.duplicate").count()).isZero();
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
		stubFirstDelivery();

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
	void ingest_archivesOversizedPayloadAsDlq() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getJMSCorrelationID()).thenReturn(null);
		when(textMessage.getText()).thenReturn("x".repeat(MAX_PAYLOAD_BYTES + 1));
		when(textMessage.getStringProperty("JMS_IBM_Format")).thenReturn(null);
		when(textMessage.getJMSType()).thenReturn(null);
		stubFirstDelivery();

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(MessageStatus.DLQ);
		assertThat(captor.getValue().getPayload()).contains("Payload exceeds max size");
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isEqualTo(1);
	}

	@Test
	void ingest_archivesWhenRedeliveryExceededAsDlq() throws Exception {
		when(textMessage.getJMSMessageID()).thenReturn("ID:1");
		when(textMessage.getJMSCorrelationID()).thenReturn(null);
		when(textMessage.propertyExists("JMSXDeliveryCount")).thenReturn(true);
		when(textMessage.getIntProperty("JMSXDeliveryCount")).thenReturn(MAX_REDELIVERY + 1);

		assertThat(service.ingest(textMessage, "DEV.QUEUE.1")).isEqualTo(IngestOutcome.DLQ);

		ArgumentCaptor<MqMessage> captor = ArgumentCaptor.forClass(MqMessage.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(MessageStatus.DLQ);
		assertThat(captor.getValue().getPayload()).contains("exceeded max redelivery");
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isEqualTo(1);
		verify(textMessage, never()).getText();
	}

	@Test
	void ingest_archivesUnsupportedTypeAsDlq() throws Exception {
		BytesMessage bytesMessage = mock(BytesMessage.class);
		when(bytesMessage.getJMSMessageID()).thenReturn("ID:2");
		when(bytesMessage.getJMSCorrelationID()).thenReturn(null);
		when(bytesMessage.propertyExists("JMSXDeliveryCount")).thenReturn(true);
		when(bytesMessage.getIntProperty("JMSXDeliveryCount")).thenReturn(1);

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
		stubFirstDelivery();
		when(repository.save(any())).thenThrow(new QueryTimeoutException("db timeout"));

		assertThatThrownBy(() -> service.ingest(textMessage, "DEV.QUEUE.1"))
				.isInstanceOf(QueryTimeoutException.class);

		assertThat(meterRegistry.counter("mq.ingest.success").count()).isZero();
		assertThat(meterRegistry.counter("mq.ingest.failure").count()).isZero();
	}

	private void stubFirstDelivery() throws Exception {
		when(textMessage.propertyExists("JMSXDeliveryCount")).thenReturn(true);
		when(textMessage.getIntProperty("JMSXDeliveryCount")).thenReturn(1);
	}

	private static DataIntegrityViolationException uniqueMessageIdViolation() {
		return new DataIntegrityViolationException(
				"duplicate key value violates unique constraint \"uq_mq_message_message_id\"");
	}
}
