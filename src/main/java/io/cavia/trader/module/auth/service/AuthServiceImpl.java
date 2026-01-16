package io.cavia.trader.module.auth.service;

import io.cavia.trader.common.email.EmailService;
import io.cavia.trader.common.exception.ApiException;
import io.cavia.trader.common.exception.ErrorCode;
import io.cavia.trader.module.auth.aop.RequiresRecaptcha;
import io.cavia.trader.module.auth.dto.SignupRequestDto;
import io.cavia.trader.module.auth.dto.TokenDto;
import io.cavia.trader.module.auth.entity.EmailVerification;
import io.cavia.trader.module.auth.entity.RefreshToken;
import io.cavia.trader.module.auth.jwt.JwtUtil;
import io.cavia.trader.module.auth.repository.EmailVerificationRepository;
import io.cavia.trader.module.auth.repository.RefreshTokenRepository;
import io.cavia.trader.module.member.entity.Member;
import io.cavia.trader.module.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final EmailService emailService;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtUtil jwtUtil;
    private final TemplateEngine templateEngine;
    private final MemberService memberService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${auth.access-token.expiration.time:1800000L}")
    private long accessTokenExpirationTime; // application.properties 에서 주입받은 액세스 토큰 만료 시간, 기본값 30분

    @Value("${auth.refresh-token.expiration.time:604800000L}")
    private long refreshTokenExpirationTime; // application.properties 에서 주입받은 리프레시 토큰 만료시간, 기본값 7일

    @RequiresRecaptcha
    @Override
    public void sendVerificationEmail(String email) {
        String authKey = String.valueOf((int) (Math.random() * 900000 + 100000));

        sendAuthEmail(email, authKey);
        emailVerificationRepository.save(EmailVerification.create(email, authKey, 60));
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyCodeForPasswordReset(String email, String authKey) {
        verifyAuthKey(email, authKey);
        memberService.getMemberByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyCodeForSignup(String email, String authKey) {
        verifyAuthKey(email, authKey);
        memberService.validateDuplicateEmail(email);
    }

    @Transactional(readOnly = true)
    private void verifyAuthKey(String email, String authKey) {
        EmailVerification emailVerification = emailVerificationRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
        if (!emailVerification.getVerificationKey().equals(authKey)) {
            throw new ApiException(ErrorCode.INVALID_AUTH_KEY);
        }
        if (emailVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.EXPIRED_AUTH_KEY);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateDuplicateNickname(String nickname) {
        memberService.validateDuplicateNickname(nickname);
    }

    @Override
    public Member register(SignupRequestDto requestDto) {
        verifyAuthKey(requestDto.getEmail(), requestDto.getAuthKey());
        memberService.validateDuplicateEmail(requestDto.getEmail());
        memberService.validateDuplicateNickname(requestDto.getNickname());

        Member member = memberService.createMember(
                requestDto.getEmail(),
                requestDto.getPassword(),
                requestDto.getNickname()
        );
        log.debug("회원가입 완료 : member = {}", member);
        return member;
    }

    @Override
    public TokenDto login(String email, String password, String userAgent) {
        try {
            // 로그인 아이디와 비밀번호 검증
            Member member = memberService.getMemberByEmail(email);
            memberService.validatePassword(member.getId(), password);

            // 액세스 토큰 생성
            String accessToken = jwtUtil.createToken(member.getId(), member.getRole(), accessTokenExpirationTime);

            // 리프레시 토큰 생성 (UUID 사용해서 고유 토큰 생성)
            String refreshTokenValue = UUID.randomUUID().toString();

            // 리프레시 토큰을 DB에 저장
            refreshTokenRepository.save(RefreshToken.create(
                    member.getId(),
                    refreshTokenValue,
                    userAgent,
                    refreshTokenExpirationTime // 설정된 만료 시간(ms)
            ));
            return new TokenDto("Bearer", accessToken, accessTokenExpirationTime / 1000, refreshTokenValue);
        } catch (ApiException e) {
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
    }

    @Override
    public void resetPassword(String email, String authKey, String rawPassword) {
        verifyAuthKey(email, authKey);
        Member member = memberService.getMemberByEmail(email);
        memberService.changePassword(member.getId(), rawPassword);
    }

    @Override
    public String getTermAsRawText(String type) {
        String path = "texts/terms/" + type + ".md";
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new ApiException(ErrorCode.TERMS_NOT_FOUND);
        }
        try {
            Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.TERMS_OUTPUT_FAILED);
        }
    }

    /**
     * 사용자의 이메일 인증을 위해 인증키가 포함된 이메일을 발송합니다.
     *
     * @param to      메일 받을 주소
     * @param authKey 사용자에게 전달할 이메일 인증을 위한 인증키
     */
    private void sendAuthEmail(String to, String authKey) {
        Context context = new Context();
        context.setVariable("username", to);
        context.setVariable("authKey", authKey);
        // 템플릿을 사용하여 HTML 본문 생성
        String htmlBody = templateEngine.process("email/auth-email", context);

        try {
            emailService.sendEmail(to, "[TRADER.IO] 이메일 인증", htmlBody);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
