package com.reto.order.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryEntryResponse {

    private Integer sequenceNumber;
    private String previousStatus;
    private String newStatus;
    private String reason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant changedAt;
}