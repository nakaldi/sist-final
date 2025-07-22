package io.cavia.trader.module.jwt;

import io.cavia.trader.module.auth.security.UserDetailsImpl;
import io.cavia.trader.module.member.entity.Member;
import io.cavia.trader.module.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // HTTP 요청 헤더에서 'Authorization' 값을 가져옵니다.
        String bearerToken = request.getHeader(JwtUtil.AUTHORIZATION_HEADER);

        // bearerToken이 null이 아니고, "Bearer "로 시작하는지 확인합니다.
        if (bearerToken != null && bearerToken.startsWith(JwtUtil.BEARER_PREFIX)) {
            // 'Bearer ' 부분을 잘라내어 순수한 토큰만 추출합니다.
            String token = jwtUtil.substringToken(bearerToken);

            // 토큰 유효성 검증
            if (jwtUtil.validateToken(token)) {
                Claims userInfo = jwtUtil.getUserInfoFromToken(token);
                log.debug("토큰 유효성 검증 완료 후 jwt Claims: {}", userInfo);

                try {
                    Long userId = Long.parseLong(userInfo.getSubject());
                    setAuthentication(userId);
                } catch (Exception e) {
                    log.error("인증 처리 중 예외 발생: {}", e.getMessage());
                    // 여기서 response에 에러를 담아 보낼 수도 있습니다.
                }
            }
        }

        // 다음 필터로 요청을 전달합니다.
        // 위에서 토큰 검증 및 인증 처리를 하지 못했더라도,
        // 다음 필터(그리고 최종적으로는 SecurityConfig의 인가 설정)가
        // 해당 요청을 처리할 수 있도록 무조건 호출해야 합니다.
        filterChain.doFilter(request, response);
    }

    /**
     * 인증 처리 메서드
     *
     * @param userId JWT에서 추출한 사용자 고유 번호
     */
    private void setAuthentication(Long userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        Member member = memberRepository.findById(userId).orElseThrow(
                () -> new UsernameNotFoundException("해당 id 값으로 사용자를 찾을 수 없습니다."));
        // UserDetailsService를 통해 사용자 정보를 로드합니다.
        UserDetails userDetails = new UserDetailsImpl(member);
        // 인증 객체를 생성합니다.
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        // SecurityContext에 인증 정보를 설정합니다.
        context.setAuthentication(authentication);
        // SecurityContextHolder에 SecurityContext를 설정합니다.
        SecurityContextHolder.setContext(context);
    }
}