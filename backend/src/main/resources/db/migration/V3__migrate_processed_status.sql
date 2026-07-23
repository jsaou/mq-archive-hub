-- PROCESSED removed from MessageStatus; map legacy rows to RECEIVED
UPDATE mq_message SET status = 'RECEIVED' WHERE status = 'PROCESSED';
