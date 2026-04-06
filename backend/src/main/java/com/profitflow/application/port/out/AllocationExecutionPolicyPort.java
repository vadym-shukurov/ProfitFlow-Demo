package com.profitflow.application.port.out;

/**
 * Segregation-of-duties and approval gate for allocation execution.
 *
 * <p>The default implementation allows any authorised caller to run allocations immediately.
 * Replace the bean with one that checks approvals, change tickets, or maker–checker state.
 */
public interface AllocationExecutionPolicyPort {

    /**
     * Verifies that the given principal may execute an allocation run now.
     *
     * @param username resolved principal name (never {@code null})
     * @throws com.profitflow.application.exception.ResourceConflictException
     *         or another runtime exception if execution must be blocked
     */
    void assertMayExecute(String username);
}
