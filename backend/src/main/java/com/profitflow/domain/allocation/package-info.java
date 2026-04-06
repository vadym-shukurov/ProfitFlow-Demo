/**
 * Two-stage ABC allocation core: {@link com.profitflow.domain.allocation.AllocationEngine}
 * composes {@link com.profitflow.domain.allocation.ProportionalAllocator} for
 * resources→activities and activities→products. Cash-conserving proportional splits;
 * pure Java with no framework or I/O — safe for deterministic finance calculations
 * and exhaustive unit tests.
 */
package com.profitflow.domain.allocation;
