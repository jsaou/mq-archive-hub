package com.bank.mq.archive.service;

public enum IngestOutcome {
	SUCCESS,
	DUPLICATE,
	ERROR,
	DLQ
}
