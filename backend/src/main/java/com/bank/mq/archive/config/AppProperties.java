package com.bank.mq.archive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
		@NotNull @Valid MqProperties mq,
		@NotNull @Valid ApiProperties api) {

	public record MqProperties(
			@NotBlank String queueName,
			@NotBlank String dlqName,
			@NotBlank String concurrency) {
	}

	public record ApiProperties(
			@NotBlank String basePath,
			@Positive int maxPageSize,
			@Positive int defaultPageSize) {
	}
}
