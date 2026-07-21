package com.bank.mq.archive.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.jms.ConnectionFactory;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Import(PostgresTestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

	/** Avoids connecting to a real IBM MQ broker; ITs invoke the listener directly. */
	@MockitoBean
	@SuppressWarnings("unused")
	private ConnectionFactory connectionFactory;
}
