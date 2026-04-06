/**
 * Application services: orchestrate use cases, own {@code @Transactional} boundaries,
 * call domain logic, and persist via outbound ports. Cross-cutting audit is applied
 * with {@link com.profitflow.application.audit.AuditedOperation} (implemented by
 * {@code com.profitflow.security.AuditAspect}).
 */
package com.profitflow.application.service;
