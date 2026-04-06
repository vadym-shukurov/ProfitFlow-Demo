package com.profitflow.infrastructure;

import com.profitflow.application.port.out.AllocationExecutionPolicyPort;
import org.springframework.stereotype.Component;

/**
 * Default SoD gate: no extra approval step (suitable for demos and early production).
 */
@Component
public class PermissiveAllocationExecutionPolicy implements AllocationExecutionPolicyPort {

    @Override
    public void assertMayExecute(String username) {
        // Extension point: consult approval workflow, change tickets, etc.
    }
}
