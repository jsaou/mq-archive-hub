package com.bank.mq.archive.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.mq.archive.domain.MqMessage;

public interface MqMessageRepository extends JpaRepository<MqMessage, Long> {

	Optional<MqMessage> findByMessageId(String messageId);

	boolean existsByMessageId(String messageId);
}
