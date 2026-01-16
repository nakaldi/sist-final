package io.cavia.trader.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OAuth 2.0 (RFC 6749)에 정의된 성공적인 토큰 발급 응답을 나타내는 DTO.
 */
@Getter
@AllArgsConstructor
public class TokenDto {

    /**
     * 발급된 토큰의 타입입니다. 항상 "Bearer"가 사용됩니다.
     */
    private final String grantType;

    /**
     * 보호된 리소스에 접근하는 데 사용되는 액세스 토큰 문자열입니다.
     */
    private final String accessToken;

    /**
     * 액세스 토큰의 유효 기간(초 단위)입니다.
     */
    private final Long expiresIn;

    /**
     * 액세스 토큰이 만료되었을 때 새로운 액세스 토큰을 발급받는 데 사용되는 리프레시 토큰입니다.
     */
    private final String refreshToken;
}