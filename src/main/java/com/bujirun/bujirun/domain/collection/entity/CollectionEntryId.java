package com.bujirun.bujirun.domain.collection.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CollectionEntryId implements Serializable {
    private UUID user;
    private UUID spot;
}