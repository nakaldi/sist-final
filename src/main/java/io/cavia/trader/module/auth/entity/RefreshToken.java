package io.cavia.trader.module.auth.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    private Long id;
    private Long memberId;
    private String tokenValue;
    private String userAgent;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    /**
     * 리프레시 토큰 객체를 생성하는 정적 팩토리 메서드입니다.
     *
     * @param memberId          리프레시 토큰 발급 대상 사용자 고유 id
     * @param tokenValue        생성된 리프레시 토큰 값
     * @param validityInMinutes 유효 기간(분)
     * @return 초기화된 RefreshToken 객체
     */
    public static RefreshToken create(Long memberId, String tokenValue, String userAgent, int validityInMinutes) {
        RefreshToken token = new RefreshToken();
        token.memberId = memberId;
        token.tokenValue = tokenValue;
        token.userAgent = userAgent;
        token.issuedAt = LocalDateTime.now();
        token.expiresAt = token.issuedAt.plusMinutes(validityInMinutes);
        return token;
    }

}
