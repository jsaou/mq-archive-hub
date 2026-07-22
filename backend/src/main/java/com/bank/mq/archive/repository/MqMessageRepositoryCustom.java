package com.bank.mq.archive.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.bank.mq.archive.dto.MqMessageSummaryDto;
import com.bank.mq.archive.entity.MqMessage;

public interface MqMessageRepositoryCustom {

	/**
	 * Finds messages without loading the payload.
	 */
	Page<MqMessageSummaryDto> findSummaries(Specification<MqMessage> spec, Pageable pageable);
}
