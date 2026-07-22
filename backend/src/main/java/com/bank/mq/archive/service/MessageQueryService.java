package com.bank.mq.archive.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.mq.archive.config.AppProperties;
import com.bank.mq.archive.dto.MqMessageDetailDto;
import com.bank.mq.archive.dto.MqMessageSearchCriteria;
import com.bank.mq.archive.dto.MqMessageSummaryDto;
import com.bank.mq.archive.entity.MqMessage;
import com.bank.mq.archive.exception.MessageNotFoundException;
import com.bank.mq.archive.repository.MqMessageRepository;
import com.bank.mq.archive.repository.MqMessageSpecs;

@Service
public class MessageQueryService {

	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "receivedAt");

	// Sort properties allowed for message listing.
	static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
			"id",
			"messageId",
			"correlationId",
			"contentType",
			"status",
			"receivedAt");

	private final MqMessageRepository repository;
	private final int maxPageSize;
	private final int defaultPageSize;

	public MessageQueryService(MqMessageRepository repository, AppProperties appProperties) {
		this.repository = repository;
		this.maxPageSize = appProperties.api().maxPageSize();
		this.defaultPageSize = appProperties.api().defaultPageSize();
	}

	@Transactional(readOnly = true)
	public Page<MqMessageSummaryDto> search(MqMessageSearchCriteria criteria, Pageable pageable) {
		Pageable safePageable = clamp(pageable);
		return repository.findSummaries(MqMessageSpecs.withFilters(criteria), safePageable);
	}

	@Transactional(readOnly = true)
	public MqMessageDetailDto getById(Long id) {
		return repository.findById(id)
				.map(MqMessageDetailDto::from)
				.orElseThrow(() -> new MessageNotFoundException(id));
	}

	Pageable clamp(Pageable pageable) {
		if (pageable == null || pageable.isUnpaged()) {
			return PageRequest.of(0, defaultPageSize, DEFAULT_SORT);
		}
		int page = Math.max(pageable.getPageNumber(), 0);
		int size = Math.min(Math.max(pageable.getPageSize(), 1), maxPageSize);
		Sort sort = pageable.getSort().isSorted() ? validateSort(pageable.getSort()) : DEFAULT_SORT;
		return PageRequest.of(page, size, sort);
	}

	private static Sort validateSort(Sort sort) {
		for (Sort.Order order : sort) {
			if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
				throw new PropertyReferenceException(
						order.getProperty(),
						TypeInformation.of(MqMessage.class),
						List.of());
			}
		}
		return sort;
	}
}
