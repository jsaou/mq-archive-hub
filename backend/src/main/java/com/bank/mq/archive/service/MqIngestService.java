package com.bank.mq.archive.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	private final MqMessageRepository repository;
	private final Counter successCounter;
	private final Counter duplicateCounter;
	private final Counter failureCounter;

	public MqIngestService(MqMessageRepository repository, MeterRegistry meterRegistry) {
		this.repository = repository;
		this.successCounter = meterRegistry.counter("mq.ingest.success");
		this.duplicateCounter = meterRegistry.counter("mq.ingest.duplicate");
		this.failureCounter = meterRegistry.counter("mq.ingest.failure");
	}

	/**
	 * Archives the message. {@link IngestOutcome#DLQ} means the caller must park on the MQ DLQ.
	 * Duplicates ACK; transient errors propagate for JMS redelivery.
	 */
	@Transactional
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

		if (repository.existsByMessageId(messageId)) {
			duplicateCounter.increment();
			log.debug("Duplicate message ignored: {}", messageId);
			// Prior DLQ archive without successful park → ask listener to park again
			return repository.findByMessageId(messageId)
					.filter(existing -> existing.getStatus() == MessageStatus.DLQ)
					.map(existing -> IngestOutcome.DLQ)
					.orElse(IngestOutcome.DUPLICATE);
		}

		try {
			MqMessageDto dto = MqMessageDto.forIngest(
					messageId,
					message.getJMSCorrelationID(),
					queueName,
					extractPayload(message),
					resolveContentType(message));
			repository.save(dto.toEntity());
			successCounter.increment();
			return IngestOutcome.SUCCESS;
		}
		catch (DataIntegrityViolationException ex) {
			duplicateCounter.increment();
			log.debug("Duplicate message ignored on insert: {}", messageId);
			return IngestOutcome.DUPLICATE;
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

	private void persistFailure(
			String messageId,
			String correlationId,
			String queueName,
			String payload,
			String contentType,
			MessageStatus status) {
		if (repository.existsByMessageId(messageId)) {
			duplicateCounter.increment();
			log.debug("Duplicate failure archive ignored: {}", messageId);
			return;
		}
		try {
			repository.save(MqMessageDto.forFailure(
					messageId, correlationId, queueName, payload, contentType, status).toEntity());
		}
		catch (DataIntegrityViolationException ex) {
			duplicateCounter.increment();
			log.debug("Duplicate failure archive ignored on insert: {}", messageId);
		}
	}

	private static MessageStatus toStatus(Disposition disposition) {
		return disposition == Disposition.ERROR ? MessageStatus.ERROR : MessageStatus.DLQ;
	}

	private static String extractPayload(Message message) throws JMSException {
		if (message instanceof TextMessage textMessage) {
			String text = textMessage.getText();
			if (text == null) {
				throw new PermanentIngestException("TextMessage payload is null", Disposition.ERROR);
			}
			return text;
		}
		throw new PermanentIngestException(
				"Unsupported message type: " + message.getClass().getSimpleName(),
				Disposition.DLQ);
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
