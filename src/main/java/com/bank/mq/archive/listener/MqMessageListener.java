package com.bank.mq.archive.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.bank.mq.archive.service.MqIngestService;
import com.bank.mq.archive.service.PermanentIngestException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

@Component
public class MqMessageListener {

	private static final Logger log = LoggerFactory.getLogger(MqMessageListener.class);

	private final MqIngestService ingestService;
	private final String queueName;
	private final String dlqName;
	private final Counter dlqCounter;

	public MqMessageListener(
			MqIngestService ingestService,
			MeterRegistry meterRegistry,
			@Value("${app.mq.queue-name}") String queueName,
			@Value("${app.mq.dlq-name}") String dlqName) {
		this.ingestService = ingestService;
		this.queueName = queueName;
		this.dlqName = dlqName;
		this.dlqCounter = meterRegistry.counter("mq.ingest.dlq");
	}

	@JmsListener(destination = "${app.mq.queue-name}")
	public void onMessage(Message message, Session session) throws JMSException {
		// Poison → DLQ (same session); other errors propagate for JMS rollback
		try {
			ingestService.ingest(message, queueName);
		}
		catch (PermanentIngestException ex) {
			parkOnDlq(message, session, ex);
		}
	}

	// Same-session send keeps consume + DLQ atomic under sessionTransacted
	private void parkOnDlq(Message message, Session session, PermanentIngestException cause)
			throws JMSException {
		log.warn("Parking poison message on {}: {}", dlqName, cause.getMessage());
		try (MessageProducer producer = session.createProducer(session.createQueue(dlqName))) {
			producer.send(message);
		}
		dlqCounter.increment();
	}
}
