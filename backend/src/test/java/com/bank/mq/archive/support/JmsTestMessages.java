package com.bank.mq.archive.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

public final class JmsTestMessages {

	private JmsTestMessages() {
	}

	public static TextMessage textMessage(
			String messageId,
			String correlationId,
			String payload,
			String contentType) throws JMSException {
		TextMessage message = mock(TextMessage.class);
		when(message.getJMSMessageID()).thenReturn(messageId);
		when(message.getJMSCorrelationID()).thenReturn(correlationId);
		when(message.getText()).thenReturn(payload);
		when(message.getStringProperty("JMS_IBM_Format")).thenReturn(null);
		when(message.getJMSType()).thenReturn(contentType);
		when(message.propertyExists("JMSXDeliveryCount")).thenReturn(true);
		when(message.getIntProperty("JMSXDeliveryCount")).thenReturn(1);
		return message;
	}

	public static BytesMessage bytesMessage(String messageId) throws JMSException {
		BytesMessage message = mock(BytesMessage.class);
		when(message.getJMSMessageID()).thenReturn(messageId);
		when(message.propertyExists("JMSXDeliveryCount")).thenReturn(true);
		when(message.getIntProperty("JMSXDeliveryCount")).thenReturn(1);
		return message;
	}
}
