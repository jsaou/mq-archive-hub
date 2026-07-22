package com.bank.mq.archive.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class SqlCaptureTestConfig {

	@Bean
	CapturingStatementInspector capturingStatementInspector() {
		return new CapturingStatementInspector();
	}

	@Bean
	HibernatePropertiesCustomizer capturingStatementInspectorCustomizer(
			CapturingStatementInspector inspector) {
		return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
	}

	public static final class CapturingStatementInspector implements StatementInspector {

		private final List<String> statements = new CopyOnWriteArrayList<>();

		@Override
		public String inspect(String sql) {
			statements.add(sql);
			return sql;
		}

		public void clear() {
			statements.clear();
		}

		public List<String> statements() {
			return List.copyOf(statements);
		}

		public List<String> selectStatements() {
			List<String> selects = new ArrayList<>();
			for (String sql : statements) {
				String normalized = sql.toLowerCase(Locale.ROOT);
				if (normalized.contains("select") && !normalized.contains("count(")) {
					selects.add(sql);
				}
			}
			return selects;
		}
	}
}
