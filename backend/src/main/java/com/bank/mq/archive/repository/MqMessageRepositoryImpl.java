package com.bank.mq.archive.repository;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.bank.mq.archive.dto.MqMessageSummaryDto;
import com.bank.mq.archive.entity.MqMessage;

public class MqMessageRepositoryImpl implements MqMessageRepositoryCustom {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Page<MqMessageSummaryDto> findSummaries(Specification<MqMessage> spec, Pageable pageable) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<MqMessageSummaryDto> query = cb.createQuery(MqMessageSummaryDto.class);
		Root<MqMessage> root = query.from(MqMessage.class);
		query.select(cb.construct(
				MqMessageSummaryDto.class,
				root.get("id"),
				root.get("messageId"),
				root.get("correlationId"),
				root.get("contentType"),
				root.get("status"),
				root.get("receivedAt")));
		applyPredicate(spec, root, query, cb);
		applySort(pageable.getSort(), root, query, cb);

		TypedQuery<MqMessageSummaryDto> typedQuery = entityManager.createQuery(query);
		if (pageable.isPaged()) {
			typedQuery.setFirstResult((int) pageable.getOffset());
			typedQuery.setMaxResults(pageable.getPageSize());
		}
		List<MqMessageSummaryDto> content = typedQuery.getResultList();

		long total = count(spec);
		return new PageImpl<>(content, pageable, total);
	}

	private long count(Specification<MqMessage> spec) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
		Root<MqMessage> root = countQuery.from(MqMessage.class);
		countQuery.select(cb.count(root));
		applyPredicate(spec, root, countQuery, cb);
		return entityManager.createQuery(countQuery).getSingleResult();
	}

	private static void applyPredicate(
			Specification<MqMessage> spec,
			Root<MqMessage> root,
			CriteriaQuery<?> query,
			CriteriaBuilder cb) {
		if (spec == null) {
			return;
		}
		Predicate predicate = spec.toPredicate(root, query, cb);
		if (predicate != null) {
			query.where(predicate);
		}
	}

	private static void applySort(
			Sort sort,
			Root<MqMessage> root,
			CriteriaQuery<?> query,
			CriteriaBuilder cb) {
		if (sort == null || sort.isUnsorted()) {
			return;
		}
		List<Order> orders = new ArrayList<>();
		for (Sort.Order order : sort) {
			orders.add(order.isAscending()
					? cb.asc(root.get(order.getProperty()))
					: cb.desc(root.get(order.getProperty())));
		}
		query.orderBy(orders);
	}
}
