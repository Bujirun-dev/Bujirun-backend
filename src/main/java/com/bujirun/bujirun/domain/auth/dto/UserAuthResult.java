package com.bujirun.bujirun.domain.auth.dto;

import com.bujirun.bujirun.domain.auth.entity.User;

public record UserAuthResult(User user, boolean isNewUser) {}
