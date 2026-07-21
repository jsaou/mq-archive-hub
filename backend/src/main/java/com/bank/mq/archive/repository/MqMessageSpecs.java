package com.bank.mq.archive.repository;

import org.springframework.data.jpa.domain.Specification;

import com.bank.mq.archive.dto.MqMessageSearchCriteria;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;

public final class MqMessageSpecs {

	private MqMessageSpecs() {
	}

	// Builds JPA predicates from search criteria (keeps the repository data-access only)
	public static Specification<MqMessage> withFilters(MqMessageSearchCriteria criteria) {
		if (criteria == null) {
			return Specification.unrestricted();
		}
		return Specification
				.where(hasQueueName(criteria.queueName()))
				.and(hasStatus(criteria.status()))
				.and(hasMessageId(criteria.messageId()))
				.and(hasCorrelationId(criteria.correlationId()));
	}

	private static Specification<MqMessage> hasQueueName(String queueName) {
		return (root, query, cb) -> blank(queueName)
				? null
				: cb.equal(root.get("queueName"), queueName);
	}

	private static Specification<MqMessage> hasStatus(MessageStatus status) {
		return (root, query, cb) -> status == null
				? null
				: cb.equal(root.get("status"), status);
	}

	private static Specification<MqMessage> hasMessageId(String messageId) {
		return (root, query, cb) -> blank(messageId)
				? null
				: cb.equal(root.get("messageId"), messageId);
	}

	private static Specification<MqMessage> hasCorrelationId(String correlationId) {
		return (root, query, cb) -> blank(correlationId)
				? null
				: cb.equal(root.get("correlationId"), correlationId);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
