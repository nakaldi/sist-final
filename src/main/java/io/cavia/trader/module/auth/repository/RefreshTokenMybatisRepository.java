package io.cavia.trader.module.auth.repository;

import io.cavia.trader.module.auth.entity.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenMybatisRepository implements RefreshTokenRepository {

    private final RefreshTokenMapper refreshTokenMapper;

    @Override
    public void save(RefreshToken refreshToken) {
        refreshTokenMapper.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByTokenValue(String tokenValue) {
        return refreshTokenMapper.findByTokenValue(tokenValue);
    }

    @Override
    public void deleteByTokenValue(String tokenValue) {
        refreshTokenMapper.deleteByTokenValue(tokenValue);
    }
}
