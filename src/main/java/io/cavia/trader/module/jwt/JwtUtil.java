package io.cavia.trader.module.jwt;

import io.cavia.trader.module.member.entity.MemberRoleEnum;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    // HTTP 요청 헤더에서 JWT를 담는 데 사용될 키
    public static final String AUTHORIZATION_HEADER = "Authorization";
    // JWT 토큰 값 앞에 붙는 접두사 (Bearer 스킴)
    public static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret.key}")
    private String secretKey; // application.properties에서 주입받은 비밀 키

    @Value("${jwt.token.expiration.time}")
    private long tokenExpirationTime; // application.properties에서 주입받은 토큰 만료 시간

    private Key key; // JWT 서명에 사용할 키 객체

    // @PostConstruct: 의존성 주입이 완료된 후 실행되는 초기화 메서드
    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 사용자 고유번호를 받아 JWT를 생성하는 메서드
     *
     * @param userId 사용자 고유번호
     * @return 생성된 JWT 문자열
     */
    public String createToken(Long userId, MemberRoleEnum role) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + tokenExpirationTime);

        return Jwts.builder()
                .setSubject(userId.toString()) // 토큰의 주체(사용자 이름) 설정
                .claim("roles", role)
                .setIssuedAt(now) // 토큰 발급 시간 설정
                .setExpiration(expirationDate) // 토큰 만료 시간 설정
                .signWith(key, SignatureAlgorithm.HS256) // 사용할 암호화 알고리즘과 키로 서명
                .compact(); // JWT 문자열로 압축
    }

    /**
     * HTTP 요청에서 'Bearer ' 접두사를 제거하고 순수한 토큰 문자열을 반환하는 메서드
     *
     * @param bearerToken 'Bearer '로 시작하는 토큰 문자열
     * @return 순수한 JWT 문자열
     */
    public String substringToken(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        throw new NullPointerException("토큰이 유효하지 않습니다.");
    }

    /**
     * 주어진 토큰의 유효성을 검증하는 메서드 (0.12.5 버전용)
     *
     * @param token 검증할 JWT 문자열
     * @return 토큰이 유효하면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            // Jwts.parser()를 사용해 JwtParserBuilder를 얻고,
            // verifyWith()로 서명 검증에 사용할 키를 설정한 후,
            // build()로 JwtParser를 생성합니다.
            // 그 다음 parseSignedClaims()로 토큰을 파싱하여 검증합니다.
            Jwts.parser()
                    .verifyWith((SecretKey) key) // key가 SecretKey 타입이어야 합니다.
                    .build()
                    .parseSignedClaims(token);
            log.debug("JWT 인증됨");
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("유효하지 않은 JWT 서명입니다.", e);
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다.", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 클레임 문자열이 비어있습니다.", e);
        }
        log.debug("JWT 인증 실패함");
        return false;
    }

    /**
     * 유효한 토큰에서 사용자 정보를 추출하는 메서드 (0.12.5 버전용)
     *
     * @param token 유효성이 검증된 JWT 문자열
     * @return 토큰에 담긴 사용자 정보(Claims) 객체
     */
    public Claims getUserInfoFromToken(String token) {
        // validateToken()을 통과한 토큰이므로, 예외 처리 없이 바로 파싱합니다.
        // getPayload() 대신 getBody()를 사용했던 이전 버전과 달리, 0.12.x 에서는 getPayload()를 사용합니다.
        return Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}