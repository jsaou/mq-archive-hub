package com.bank.mq.archive.domain;

public enum MessageStatus {
	RECEIVED,
	PROCESSED,
	ERROR,
	DLQ
}
