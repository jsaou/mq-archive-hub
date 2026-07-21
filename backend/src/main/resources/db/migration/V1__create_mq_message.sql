CREATE TABLE mq_message (
    id              BIGSERIAL PRIMARY KEY,
    message_id      VARCHAR(100)  NOT NULL,
    correlation_id  VARCHAR(100),
    queue_name      VARCHAR(255)  NOT NULL,
    payload         TEXT          NOT NULL,
    content_type    VARCHAR(100),
    status          VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',
    received_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_mq_message_message_id UNIQUE (message_id)
);

CREATE INDEX idx_mq_message_received_at ON mq_message (received_at DESC);
CREATE INDEX idx_mq_message_correlation_id ON mq_message (correlation_id);
CREATE INDEX idx_mq_message_status ON mq_message (status);
