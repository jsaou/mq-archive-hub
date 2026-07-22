package com.bank.mq.archive.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.mq.archive.dto.MqMessageDetailDto;
import com.bank.mq.archive.dto.MqMessageSearchCriteria;
import com.bank.mq.archive.dto.MqMessageSummaryDto;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;
import com.bank.mq.archive.exception.MessageNotFoundException;
import com.bank.mq.archive.service.MessageQueryService;

@WebMvcTest(controllers = MqMessageController.class)
class MqMessageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MessageQueryService queryService;

	@Test
	void list_returnsPageWithoutPayload() throws Exception {
		MqMessageSummaryDto dto = new MqMessageSummaryDto(
				1L,
				"ID:1",
				"CORR:1",
				"text/plain",
				MessageStatus.RECEIVED,
				Instant.parse("2026-07-19T10:00:00Z"));
		when(queryService.search(any(MqMessageSearchCriteria.class), any()))
				.thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/messages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].messageId").value("ID:1"))
				.andExpect(jsonPath("$.content[0].status").value("RECEIVED"))
				.andExpect(jsonPath("$.content[0].contentType").value("text/plain"))
				.andExpect(jsonPath("$.page.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].payload").doesNotExist())
				.andExpect(jsonPath("$.content[0].queueName").doesNotExist());
	}

	@Test
	void list_forwardsFilters() throws Exception {
		MqMessageSearchCriteria criteria = new MqMessageSearchCriteria(
				"DEV.QUEUE.1", MessageStatus.RECEIVED, "ID:1", "CORR:1");
		when(queryService.search(eq(criteria), any())).thenReturn(new PageImpl<>(List.of()));

		mockMvc.perform(get("/api/v1/messages")
						.param("queueName", "DEV.QUEUE.1")
						.param("status", "RECEIVED")
						.param("messageId", "ID:1")
						.param("correlationId", "CORR:1"))
				.andExpect(status().isOk());
	}

	@Test
	void getById_returnsDetailWithPayload() throws Exception {
		MqMessageDetailDto dto = new MqMessageDetailDto(
				1L,
				"ID:1",
				null,
				"payload",
				null,
				MessageStatus.RECEIVED,
				Instant.parse("2026-07-19T10:00:00Z"));
		when(queryService.getById(1L)).thenReturn(dto);

		mockMvc.perform(get("/api/v1/messages/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.messageId").value("ID:1"))
				.andExpect(jsonPath("$.payload").value("payload"))
				.andExpect(jsonPath("$.queueName").doesNotExist());
	}

	@Test
	void getById_returns404WhenMissing() throws Exception {
		when(queryService.getById(99L)).thenThrow(new MessageNotFoundException(99L));

		mockMvc.perform(get("/api/v1/messages/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Message not found: 99"));
	}

	@Test
	void list_invalidStatusParam_returns400() throws Exception {
		mockMvc.perform(get("/api/v1/messages").param("status", "INVALID_STATUS"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invalid value 'INVALID_STATUS' for parameter 'status'"));
	}

	@Test
	void getById_invalidIdType_returns400() throws Exception {
		mockMvc.perform(get("/api/v1/messages/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invalid value 'abc' for parameter 'id'"));
	}

	@Test
	void getById_dataAccessException_returns503() throws Exception {
		when(queryService.getById(1L))
				.thenThrow(new DataAccessResourceFailureException("DB is down"));

		mockMvc.perform(get("/api/v1/messages/1"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("A database error occurred. Please try again later."));
	}

	@Test
	void list_dataAccessException_returns503() throws Exception {
		when(queryService.search(any(), any()))
				.thenThrow(new DataAccessResourceFailureException("DB is down"));

		mockMvc.perform(get("/api/v1/messages"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("A database error occurred. Please try again later."));
	}

	@Test
	void unknownResource_returns404() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Resource not found"));
	}

	@Test
	void getById_unexpectedException_returns500() throws Exception {
		when(queryService.getById(1L)).thenThrow(new RuntimeException("Unexpected"));

		mockMvc.perform(get("/api/v1/messages/1"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
	}

	@Test
	void list_invalidSortField_returns400() throws Exception {
		when(queryService.search(any(), any()))
				.thenThrow(new PropertyReferenceException("notAField", TypeInformation.of(MqMessage.class), List.of()));

		mockMvc.perform(get("/api/v1/messages").param("sort", "notAField,desc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invalid sort property: notAField"));
	}
}
