package com.bank.mq.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.mq.archive.listener.MqMessageListener;
import com.bank.mq.archive.repository.MqMessageRepository;
import com.bank.mq.archive.support.AbstractIntegrationTest;
import com.bank.mq.archive.support.JmsTestMessages;

import jakarta.jms.Session;
import jakarta.jms.TextMessage;

/**
 * End-to-end flow without a live IBM MQ broker: simulated JMS delivery → listener → DB → REST.
 */
class ArchiveFlowIT extends AbstractIntegrationTest {

	@Autowired
	private MqMessageListener listener;

	@Autowired
	private MqMessageRepository repository;

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		repository.deleteAllInBatch();
	}

	@Test
	void messageFromMqIsPersistedAndExposedViaApi() throws Exception {
		TextMessage message = JmsTestMessages.textMessage(
				"ID:e2e-1", "CORR:e2e", "{\"amount\":100}", "application/json");
		Session session = org.mockito.Mockito.mock(Session.class);

		listener.onMessage(message, session);

		assertThat(repository.findByMessageId("ID:e2e-1")).isPresent();
		Long id = repository.findByMessageId("ID:e2e-1").orElseThrow().getId();

		mockMvc.perform(get("/api/v1/messages/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messageId").value("ID:e2e-1"))
				.andExpect(jsonPath("$.correlationId").value("CORR:e2e"))
				.andExpect(jsonPath("$.payload").value("{\"amount\":100}"))
				.andExpect(jsonPath("$.status").value("RECEIVED"));

		mockMvc.perform(get("/api/v1/messages").param("messageId", "ID:e2e-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(id))
				.andExpect(jsonPath("$.content[0].contentType").value("application/json"))
				.andExpect(jsonPath("$.content[0].payload").doesNotExist());
	}
}
