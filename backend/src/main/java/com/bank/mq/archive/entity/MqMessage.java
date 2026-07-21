package com.bank.mq.archive.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mq_message")
public class MqMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "message_id", nullable = false, unique = true, length = 100)
	private String messageId;

	@Column(name = "correlation_id", length = 100)
	private String correlationId;

	@Column(name = "queue_name", nullable = false)
	private String queueName;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(name = "content_type", length = 100)
	private String contentType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MessageStatus status;

	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	protected MqMessage() {
	}

	public MqMessage(
			String messageId,
			String correlationId,
			String queueName,
			String payload,
			String contentType) {
		this.messageId = Objects.requireNonNull(messageId, "messageId");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.payload = Objects.requireNonNull(payload, "payload");
		this.correlationId = correlationId;
		this.contentType = contentType;
		this.status = MessageStatus.RECEIVED;
		this.receivedAt = Instant.now();
	}

	public void markProcessed() {
		this.status = MessageStatus.PROCESSED;
	}

	public void markError() {
		this.status = MessageStatus.ERROR;
	}

	public void markDlq() {
		this.status = MessageStatus.DLQ;
	}

	public Long getId() {
		return id;
	}

	public String getMessageId() {
		return messageId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getQueueName() {
		return queueName;
	}

	public String getPayload() {
		return payload;
	}

	public String getContentType() {
		return contentType;
	}

	public MessageStatus getStatus() {
		return status;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}
}
