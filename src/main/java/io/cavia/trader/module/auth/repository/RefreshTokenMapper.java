package io.cavia.trader.module.auth.repository;

import io.cavia.trader.module.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface RefreshTokenMapper {

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
    Optional<RefreshToken> findByTokenValue(@Param("tokenValue") String tokenValue);

    /**
     * 토큰 값으로 리프레시 토큰을 삭제합니다.
     *
     * @param tokenValue 토큰 값
     */
    void deleteByTokenValue(@Param("tokenValue") String tokenValue);
}
