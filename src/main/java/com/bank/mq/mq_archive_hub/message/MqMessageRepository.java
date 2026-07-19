package com.bank.mq.mq_archive_hub.message;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MqMessageRepository extends JpaRepository<MqMessage, Long> {

	Optional<MqMessage> findByMessageId(String messageId);

	boolean existsByMessageId(String messageId);
}
