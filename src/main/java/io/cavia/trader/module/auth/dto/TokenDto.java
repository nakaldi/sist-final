package io.cavia.trader.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenDto {
    private final String grantType;
    private final String accessToken;
    private final Long expiresIn;
    private final String refreshToken;
}