package com.bank.mq.archive.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bank.mq.archive.entity.MqMessage;

public interface MqMessageRepository
		extends JpaRepository<MqMessage, Long>, JpaSpecificationExecutor<MqMessage>, MqMessageRepositoryCustom {

	Optional<MqMessage> findByMessageId(String messageId);

	boolean existsByMessageId(String messageId);
}
