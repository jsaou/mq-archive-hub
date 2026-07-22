package com.bank.mq.archive.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.mq.archive.dto.MqMessageSearchCriteria;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.service.MessageQueryService;

@RestController
@RequestMapping("${app.api.base-path}/messages")
public class MqMessageController {

	private final MessageQueryService queryService;

	public MqMessageController(MessageQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public Page<MqMessageSummaryResponse> list(
			@RequestParam(required = false) String queueName,
			@RequestParam(required = false) MessageStatus status,
			@RequestParam(required = false) String messageId,
			@RequestParam(required = false) String correlationId,
			Pageable pageable) {
		MqMessageSearchCriteria criteria = new MqMessageSearchCriteria(
				queueName, status, messageId, correlationId);
		return queryService.search(criteria, pageable).map(MqMessageSummaryResponse::from);
	}

	@GetMapping("/{id}")
	public ResponseEntity<MqMessageDetailResponse> getById(@PathVariable Long id) {
		return ResponseEntity.ok(MqMessageDetailResponse.from(queryService.getById(id)));
	}
}
