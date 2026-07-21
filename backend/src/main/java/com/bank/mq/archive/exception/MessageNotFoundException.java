package com.bank.mq.archive.exception;

public class MessageNotFoundException extends RuntimeException {

	public MessageNotFoundException(Long id) {
		super("Message not found: " + id);
	}
}
