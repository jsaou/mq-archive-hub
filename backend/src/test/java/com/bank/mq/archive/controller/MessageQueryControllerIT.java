package com.bank.mq.archive.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;
import com.bank.mq.archive.repository.MqMessageRepository;
import com.bank.mq.archive.support.AbstractIntegrationTest;

class MessageQueryControllerIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MqMessageRepository repository;

	@BeforeEach
	void setUp() {
		repository.deleteAllInBatch();
	}

	@Test
	void list_returnsPaginatedMessages() throws Exception {
		repository.saveAndFlush(new MqMessage("ID:1", "CORR:1", "DEV.QUEUE.1", "payload-1", "text/plain"));
		repository.saveAndFlush(new MqMessage("ID:2", "CORR:2", "DEV.QUEUE.1", "payload-2", "text/plain"));
		repository.saveAndFlush(new MqMessage("ID:3", null, "OTHER.QUEUE", "payload-3", null));

		mockMvc.perform(get("/api/v1/messages").param("size", "10").param("page", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].id").exists())
				.andExpect(jsonPath("$.content[0].messageId").exists())
				.andExpect(jsonPath("$.content[0].status").exists())
				.andExpect(jsonPath("$.content[0].receivedAt").exists())
				.andExpect(jsonPath("$.content[?(@.messageId=='ID:1')].contentType").value("text/plain"))
				.andExpect(jsonPath("$.content[0].payload").doesNotExist())
				.andExpect(jsonPath("$.content[0].queueName").doesNotExist());
	}

	@Test
	void list_rejectsUnknownSortProperty() throws Exception {
		mockMvc.perform(get("/api/v1/messages").param("sort", "payload,desc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invalid sort property: payload"));
	}

	@Test
	void list_filtersByQueueNameAndStatus() throws Exception {
		MqMessage processed = new MqMessage("ID:1", null, "DEV.QUEUE.1", "a", null);
		processed.markProcessed();
		repository.saveAndFlush(processed);
		repository.saveAndFlush(new MqMessage("ID:2", null, "DEV.QUEUE.1", "b", null));
		repository.saveAndFlush(new MqMessage("ID:3", null, "OTHER.QUEUE", "c", null));

		mockMvc.perform(get("/api/v1/messages")
						.param("queueName", "DEV.QUEUE.1")
						.param("status", "RECEIVED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].messageId").value("ID:2"))
				.andExpect(jsonPath("$.content[0].status").value("RECEIVED"));
	}

	@Test
	void getById_returnsMessage() throws Exception {
		MqMessage saved = repository.saveAndFlush(
				new MqMessage("ID:1", "CORR:1", "DEV.QUEUE.1", "payload", "text/plain"));

		mockMvc.perform(get("/api/v1/messages/{id}", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(saved.getId()))
				.andExpect(jsonPath("$.messageId").value("ID:1"))
				.andExpect(jsonPath("$.correlationId").value("CORR:1"))
				.andExpect(jsonPath("$.payload").value("payload"))
				.andExpect(jsonPath("$.contentType").value("text/plain"))
				.andExpect(jsonPath("$.status").value(MessageStatus.RECEIVED.name()))
				.andExpect(jsonPath("$.queueName").doesNotExist());
	}

	@Test
	void getById_returns404WhenMissing() throws Exception {
		mockMvc.perform(get("/api/v1/messages/{id}", 99999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Message not found: 99999"));
	}
}
