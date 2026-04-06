package com.profitflow.infrastructure;

import org.junit.jupiter.api.Test;

class PermissiveAllocationExecutionPolicyTest {

    @Test
    void assertMayExecuteIsNoOp() {
        new PermissiveAllocationExecutionPolicy().assertMayExecute("any-user");
    }
}
