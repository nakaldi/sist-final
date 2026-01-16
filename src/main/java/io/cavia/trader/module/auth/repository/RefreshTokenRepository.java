package io.cavia.trader.module.auth.repository;

import io.cavia.trader.module.auth.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository {

    /**
     * 리프레시 토큰을 저장합니다.
     */
    void save(RefreshToken refreshToken);

    /**
     * 토큰 값으로 리프레시 토큰 정보를 조회합니다.
     *
     * @param tokenValue 토큰 값
     * @return Optional<RefreshToken>
     */
    Optional<RefreshToken> findByTokenValue(String tokenValue);

    /**
     * 토큰 값으로 리프레시 토큰을 삭제합니다.
     *
     * @param tokenValue 토큰 값
     */
    void deleteByTokenValue(String tokenValue);
}
