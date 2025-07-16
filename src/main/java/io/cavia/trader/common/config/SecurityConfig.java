package io.cavia.trader.common.config;

import io.cavia.trader.module.jwt.JwtAuthenticationFilter;
import io.cavia.trader.module.jwt.JwtUtil;
import io.cavia.trader.module.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    // PasswordEncoder를 Bean으로 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // JwtAuthenticationFilter를 Bean으로 등록
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, memberRepository);
    }

    // SecurityFilterChain 설정을 Bean으로 등록
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF 설정 비활성화
        http.csrf((csrf) -> csrf.disable());

        // 세션 관리 방식을 STATELESS(상태 비저장)으로 설정
        http.sessionManagement((sessionManagement) ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // 요청에 대한 접근 권한 설정
        http.authorizeHttpRequests((authorizeHttpRequests) ->
                authorizeHttpRequests
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/rankings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/notices/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/members/me", "/members/me/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().permitAll()
        );

        http.exceptionHandling(exception -> exception
                // 1. 인증되지 않은 사용자가 보호된 리소스에 접근할 때 호출될 핸들러를 설정합니다.
                .authenticationEntryPoint((request, response, authException) -> {
                    // 클라이언트에게 401 Unauthorized 에러를 응답합니다.
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.");
                })
                // 2. 인증은 되었지만, 특정 리소스에 접근할 권한이 없을 때 호출될 핸들러를 설정합니다.
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // 클라이언트에게 403 Forbidden 에러를 응답합니다.
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
                })
        );

        // 커스텀 필터인 JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 추가
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}