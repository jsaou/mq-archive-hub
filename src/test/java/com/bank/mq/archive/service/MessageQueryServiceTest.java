package com.bank.mq.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.bank.mq.archive.config.AppProperties;
import com.bank.mq.archive.dto.MqMessageDto;
import com.bank.mq.archive.dto.MqMessageSearchCriteria;
import com.bank.mq.archive.entity.MessageStatus;
import com.bank.mq.archive.entity.MqMessage;
import com.bank.mq.archive.exception.MessageNotFoundException;
import com.bank.mq.archive.repository.MqMessageRepository;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int DEFAULT_PAGE_SIZE = 20;

	@Mock
	private MqMessageRepository repository;

	private MessageQueryService service;

	@BeforeEach
	void setUp() {
		AppProperties appProperties = new AppProperties(
				new AppProperties.MqProperties("DEV.QUEUE.1", "DEV.QUEUE.2", "3-10"),
				new AppProperties.ApiProperties("/api/v1", MAX_PAGE_SIZE, DEFAULT_PAGE_SIZE));
		service = new MessageQueryService(repository, appProperties);
	}

	@Test
	void search_mapsPageAndClampsPageSize() {
		MqMessage entity = new MqMessage("ID:1", null, "DEV.QUEUE.1", "payload", null);
		when(repository.findAll(anySpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(entity)));

		MqMessageSearchCriteria criteria = new MqMessageSearchCriteria(
				"DEV.QUEUE.1", MessageStatus.RECEIVED, null, null);
		Page<MqMessageDto> result = service.search(criteria, PageRequest.of(0, 500, Sort.by("id")));

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findAll(anySpec(), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(MAX_PAGE_SIZE);
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().messageId()).isEqualTo("ID:1");
	}

	@Test
	void search_usesDefaultsWhenPageableNull() {
		when(repository.findAll(anySpec(), any(Pageable.class)))
				.thenReturn(Page.empty());

		service.search(new MqMessageSearchCriteria(null, null, null, null), null);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findAll(anySpec(), pageableCaptor.capture());
		Pageable used = pageableCaptor.getValue();
		assertThat(used.getPageNumber()).isZero();
		assertThat(used.getPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
		assertThat(used.getSort().getOrderFor("receivedAt"))
				.isNotNull()
				.extracting(Sort.Order::getDirection)
				.isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void search_rejectsNegativePageNumber() {
		when(repository.findAll(anySpec(), any(Pageable.class)))
				.thenReturn(Page.empty());

		Pageable negativePage = org.mockito.Mockito.mock(Pageable.class);
		when(negativePage.isUnpaged()).thenReturn(false);
		when(negativePage.getPageNumber()).thenReturn(-3);
		when(negativePage.getPageSize()).thenReturn(20);
		when(negativePage.getSort()).thenReturn(Sort.unsorted());

		service.search(new MqMessageSearchCriteria(null, null, null, null), negativePage);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findAll(anySpec(), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
	}

	@Test
	void search_appliesDefaultSortWhenUnsorted() {
		when(repository.findAll(anySpec(), any(Pageable.class)))
				.thenReturn(Page.empty());

		service.search(new MqMessageSearchCriteria(null, null, null, null), PageRequest.of(0, 20));

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findAll(anySpec(), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getSort().getOrderFor("receivedAt"))
				.isNotNull()
				.extracting(Sort.Order::getDirection)
				.isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void getById_returnsDto() {
		MqMessage entity = new MqMessage("ID:1", "CORR:1", "DEV.QUEUE.1", "payload", "text/plain");
		when(repository.findById(1L)).thenReturn(Optional.of(entity));

		MqMessageDto dto = service.getById(1L);

		assertThat(dto.messageId()).isEqualTo("ID:1");
		assertThat(dto.status()).isEqualTo(MessageStatus.RECEIVED);
	}

	@Test
	void getById_throwsWhenMissing() {
		when(repository.findById(eq(99L))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getById(99L))
				.isInstanceOf(MessageNotFoundException.class)
				.hasMessageContaining("99");
	}

	private static Specification<MqMessage> anySpec() {
		return ArgumentMatchers.any();
	}
}
