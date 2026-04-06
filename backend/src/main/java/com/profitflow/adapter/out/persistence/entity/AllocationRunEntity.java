package com.profitflow.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "allocation_run")
public class AllocationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_number")
    private Long runNumber;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "executed_by", nullable = false, length = 100)
    private String executedBy;

    @Column(name = "input_snapshot_hash", nullable = false, length = 64)
    private String inputSnapshotHash;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    /** JPA / persistence adapter */
    public AllocationRunEntity() {
    }

    @PrePersist
    void prePersist() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }

    public Long getRunNumber() {
        return runNumber;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public String getExecutedBy() {
        return executedBy;
    }

    public String getInputSnapshotHash() {
        return inputSnapshotHash;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setExecutedBy(String executedBy) {
        this.executedBy = executedBy;
    }

    public void setInputSnapshotHash(String inputSnapshotHash) {
        this.inputSnapshotHash = inputSnapshotHash;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }
}
