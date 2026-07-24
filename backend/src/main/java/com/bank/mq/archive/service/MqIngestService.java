package com.bank.mq.archive.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bank.mq.archive.config.AppProperties;
import com.bank.mq.archive.dto.MqMessageDto;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.exception.PermanentIngestException;
import com.bank.mq.archive.exception.PermanentIngestException.Disposition;
import com.bank.mq.archive.repository.MqMessageRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

@Service
public class MqIngestService {

	private static final Logger log = LoggerFactory.getLogger(MqIngestService.class);
	private static final String MESSAGE_ID_UNIQUE = "uq_mq_message_message_id";

	private final MqMessageRepository repository;
	private final Counter successCounter;
	private final Counter duplicateCounter;
	private final Counter failureCounter;
	private final int maxRedelivery;
	private final int maxPayloadBytes;
	private final TransactionTemplate ingestTx;
	private final TransactionTemplate duplicateLookup;

	public MqIngestService(
			MqMessageRepository repository,
			MeterRegistry meterRegistry,
			AppProperties appProperties,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.successCounter = meterRegistry.counter("mq.ingest.success");
		this.duplicateCounter = meterRegistry.counter("mq.ingest.duplicate");
		this.failureCounter = meterRegistry.counter("mq.ingest.failure");
		this.maxRedelivery = appProperties.mq().maxRedelivery();
		this.maxPayloadBytes = appProperties.mq().maxPayloadBytes();
		this.ingestTx = new TransactionTemplate(transactionManager);
		this.duplicateLookup = new TransactionTemplate(transactionManager);
		this.duplicateLookup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.duplicateLookup.setReadOnly(true);
	}

	/**
	 * Archives the message. {@link IngestOutcome#DLQ} means the caller must park on the MQ DLQ.
	 * Duplicates ACK; transient errors propagate for JMS redelivery.
	 */
	public IngestOutcome ingest(Message message, String queueName) throws JMSException {
		String messageId = message.getJMSMessageID();
		if (messageId == null || messageId.isBlank()) {
			failureCounter.increment();
			persistFailure(
					"MISSING:" + UUID.randomUUID(),
					safeCorrelationId(message),
					queueName,
					"[ingest-dlq] missing JMSMessageID",
					null,
					MessageStatus.DLQ);
			return IngestOutcome.DLQ;
		}

		int deliveryCount = deliveryCount(message);
		if (deliveryCount > maxRedelivery) {
			failureCounter.increment();
			persistFailure(
					messageId,
					safeCorrelationId(message),
					queueName,
					"[ingest-dlq] exceeded max redelivery: " + deliveryCount + " (max " + maxRedelivery + ")",
					safeContentType(message),
					MessageStatus.DLQ);
			return IngestOutcome.DLQ;
		}

		try {
			MqMessageDto dto = MqMessageDto.forIngest(
					messageId,
					message.getJMSCorrelationID(),
					queueName,
					extractPayload(message),
					resolveContentType(message));
			ingestTx.executeWithoutResult(status -> repository.save(dto.toEntity()));
			successCounter.increment();
			return IngestOutcome.SUCCESS;
		}
		catch (DataIntegrityViolationException ex) {
			if (!isMessageIdConflict(ex)) {
				throw ex;
			}
			duplicateCounter.increment();
			log.debug("Duplicate message ignored on insert: {}", messageId);
			// Prior DLQ archive without successful park → ask listener to park again
			return resolveDuplicateOutcome(messageId);
		}
		catch (PermanentIngestException ex) {
			failureCounter.increment();
			MessageStatus status = toStatus(ex.getDisposition());
			String prefix = status == MessageStatus.ERROR ? "[ingest-error] " : "[ingest-dlq] ";
			persistFailure(
					messageId,
					safeCorrelationId(message),
					queueName,
					prefix + ex.getMessage(),
					safeContentType(message),
					status);
			return status == MessageStatus.ERROR ? IngestOutcome.ERROR : IngestOutcome.DLQ;
		}
	}

	private IngestOutcome resolveDuplicateOutcome(String messageId) {
		IngestOutcome outcome = duplicateLookup.execute(status -> repository.findByMessageId(messageId)
				.filter(existing -> existing.getStatus() == MessageStatus.DLQ)
				.map(existing -> IngestOutcome.DLQ)
				.orElse(IngestOutcome.DUPLICATE));
		return outcome != null ? outcome : IngestOutcome.DUPLICATE;
	}

	private void persistFailure(
			String messageId,
			String correlationId,
			String queueName,
			String payload,
			String contentType,
			MessageStatus status) {
		try {
			ingestTx.executeWithoutResult(tx -> repository.save(MqMessageDto.forFailure(
					messageId, correlationId, queueName, payload, contentType, status).toEntity()));
		}
		catch (DataIntegrityViolationException ex) {
			if (!isMessageIdConflict(ex)) {
				throw ex;
			}
			duplicateCounter.increment();
			log.debug("Duplicate failure archive ignored on insert: {}", messageId);
		}
	}

	private static boolean isMessageIdConflict(DataIntegrityViolationException ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (String.valueOf(t).toLowerCase().contains(MESSAGE_ID_UNIQUE)) {
				return true;
			}
		}
		return false;
	}

	private static MessageStatus toStatus(Disposition disposition) {
		return disposition == Disposition.ERROR ? MessageStatus.ERROR : MessageStatus.DLQ;
	}

	/** Reads TextMessage body and rejects payloads larger than {@code maxPayloadBytes}. */
	private String extractPayload(Message message) throws JMSException {
		if (message instanceof TextMessage textMessage) {
			String text = textMessage.getText();
			if (text == null) {
				throw new PermanentIngestException("TextMessage payload is null", Disposition.ERROR);
			}
			int sizeBytes = text.getBytes(StandardCharsets.UTF_8).length;
			if (sizeBytes > maxPayloadBytes) {
				throw new PermanentIngestException(
						"Payload exceeds max size: " + sizeBytes + " bytes (max " + maxPayloadBytes + ")",
						Disposition.DLQ);
			}
			return text;
		}
		throw new PermanentIngestException(
				"Unsupported message type: " + message.getClass().getSimpleName(),
				Disposition.DLQ);
	}

	private static int deliveryCount(Message message) throws JMSException {
		if (message.propertyExists("JMSXDeliveryCount")) {
			return message.getIntProperty("JMSXDeliveryCount");
		}
		return message.getJMSRedelivered() ? 2 : 1;
	}

	private static String resolveContentType(Message message) throws JMSException {
		String format = message.getStringProperty("JMS_IBM_Format");
		return format != null ? format : message.getJMSType();
	}

	private static String safeCorrelationId(Message message) {
		try {
			return message.getJMSCorrelationID();
		}
		catch (JMSException ex) {
			log.warn("Unable to read JMSCorrelationID during failure archive", ex);
			return null;
		}
	}

	private static String safeContentType(Message message) {
		try {
			return resolveContentType(message);
		}
		catch (JMSException ex) {
			log.warn("Unable to resolve content type during failure archive", ex);
			return null;
		}
	}
}
