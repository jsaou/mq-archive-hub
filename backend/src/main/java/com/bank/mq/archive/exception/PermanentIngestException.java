package com.bank.mq.archive.exception;

import java.util.Objects;

/**
 * Non-retryable ingest failure.
 * {@link Disposition#ERROR}: archive with status ERROR (ACK, no MQ DLQ).
 * {@link Disposition#DLQ}: archive with status DLQ then park on the MQ DLQ.
 */
public class PermanentIngestException extends RuntimeException {

	public enum Disposition {
		ERROR,
		DLQ
	}

	private final Disposition disposition;

	public PermanentIngestException(String message, Disposition disposition) {
		super(message);
		this.disposition = Objects.requireNonNull(disposition, "disposition");
	}

	public Disposition getDisposition() {
		return disposition;
	}
}
