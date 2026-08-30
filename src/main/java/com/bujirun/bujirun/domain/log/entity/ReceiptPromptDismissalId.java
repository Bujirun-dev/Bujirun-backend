package com.bujirun.bujirun.domain.log.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptPromptDismissalId implements Serializable {
    private UUID userId;
    private UUID itineraryId;
}
