package com.sap.cloud.security.ams.samples.model;

import com.fasterxml.jackson.annotation.*;

/**
 * Health status model for health check endpoint
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthStatus {
    private final String status;
    private final String message;

    @JsonCreator
    public HealthStatus(
            @JsonProperty("status") String status,
            @JsonProperty("message") String message) {
        this.status = status;
        this.message = message;
    }

    public HealthStatus(String status) {
        this(status, null);
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static HealthStatus up() {
        return new HealthStatus("UP");
    }

    public static HealthStatus down(String message) {
        return new HealthStatus("DOWN", message);
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
                "status='" + status + '\'' +
                (message != null ? ", message='" + message + '\'' : "") +
                '}';
    }
}
