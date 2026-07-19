package com.bank.mq.archive.service;

// Non-retryable failure: listener must park the message on the DLQ then ACK
public class PermanentIngestException extends RuntimeException {

	public PermanentIngestException(String message) {
		super(message);
	}
}
