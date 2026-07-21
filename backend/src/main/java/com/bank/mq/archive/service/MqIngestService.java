package com.bank.mq.archive.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.mq.archive.dto.MqMessageDto;
import com.bank.mq.archive.exception.PermanentIngestException;
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

	// Duplicates ACK; permanent failures throw; transient errors propagate for redelivery
	@Transactional
	public void ingest(Message message, String queueName) throws JMSException {
		String messageId = message.getJMSMessageID();
		// Cannot archive without a stable idempotency key
		if (messageId == null || messageId.isBlank()) {
			failureCounter.increment();
			throw new PermanentIngestException("missing JMSMessageID");
		}

		// Already stored: safe to ACK
		if (repository.existsByMessageId(messageId)) {
			duplicateCounter.increment();
			log.debug("Duplicate message ignored: {}", messageId);
			return;
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
		}
		// Concurrent insert race on unique message_id
		catch (DataIntegrityViolationException ex) {
			duplicateCounter.increment();
			log.debug("Duplicate message ignored on insert: {}", messageId);
		}
		catch (PermanentIngestException ex) {
			failureCounter.increment();
			throw ex;
		}
	}

	private static String extractPayload(Message message) throws JMSException {
		if (message instanceof TextMessage textMessage) {
			String text = textMessage.getText();
			if (text == null) {
				throw new PermanentIngestException("TextMessage payload is null");
			}
			return text;
		}
		throw new PermanentIngestException(
				"Unsupported message type: " + message.getClass().getSimpleName());
	}

	private static String resolveContentType(Message message) throws JMSException {
		String format = message.getStringProperty("JMS_IBM_Format");
		return format != null ? format : message.getJMSType();
	}
}
