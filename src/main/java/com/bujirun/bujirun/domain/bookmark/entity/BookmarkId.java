package com.bujirun.bujirun.domain.bookmark.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkId implements Serializable {
    private UUID user;
    private UUID spot;
}
