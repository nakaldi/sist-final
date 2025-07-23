package io.cavia.trader.module.auth.service;

import io.cavia.trader.common.email.EmailService;
import io.cavia.trader.common.exception.ApiException;
import io.cavia.trader.common.exception.ErrorCode;
import io.cavia.trader.module.auth.aop.RequiresRecaptcha;
import io.cavia.trader.module.auth.dto.SignupRequestDto;
import io.cavia.trader.module.auth.entity.EmailVerification;
import io.cavia.trader.module.auth.repository.EmailVerificationRepository;
import io.cavia.trader.module.auth.jwt.JwtUtil;
import io.cavia.trader.module.member.entity.Member;
import io.cavia.trader.module.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional(readOnly = true)
    public String login(String email, String password) {
        try {
            Member member = memberService.getMemberByEmail(email);
            memberService.validatePassword(member.getId(), password);
            return jwtUtil.createToken(member.getId(), member.getRole());
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
