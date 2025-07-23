package io.cavia.trader.module.auth.controller;

import io.cavia.trader.common.response.ApiResponse;
import io.cavia.trader.common.response.ApiResponses;
import io.cavia.trader.module.auth.dto.*;
import io.cavia.trader.module.auth.security.UserDetailsImpl;
import io.cavia.trader.module.auth.service.AuthService;
import io.cavia.trader.module.auth.jwt.JwtUtil;
import io.cavia.trader.module.member.entity.Member;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthService authService;

    /**
     * 사용자의 이메일과 비밀번호로 로그인을 시도하고, 성공 시 JWT를 발급합니다.
     *
     * @param requestDto 로그인에 필요한 이메일, 비밀번호를 담은 DTO
     * @param response   JWT를 담을 응답 헤더
     * @return 성공 메시지를 담은 200 OK 응답
     */
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequestDto requestDto,
                                                HttpServletResponse response) {
        String token = authService.login(requestDto.getEmail(), requestDto.getPassword());
        response.setHeader(JwtUtil.AUTHORIZATION_HEADER, JwtUtil.BEARER_PREFIX + token);
        return ApiResponses.ok("토큰이 발급되었습니다", null);
    }

    /**
     * 현재 인증된 사용자의 상세 정보를 조회합니다.
     * 현재 헤더.html의 닉네임 부분을 가져오고 있는 엔드포인트로, 원래대로라면 시큐리티 필터에서 잡아야 함
     *
     * @param userDetails SecurityContextHolder에 저장된 사용자 정보
     * @return Member 엔티티를 ApiResponse.data에 담은 200 OK 성공 응답
     */
    @GetMapping("api/auth/login-checker")
    public ResponseEntity<ApiResponse<?>> getMemberInfo(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponses.ok(userDetails.getMember());
    }

    /**
     * 이메일 인증을 위해 입력된 이메일로 인증 코드를 발송합니다.
     *
     * @param requestDto 인증 코드를 받을 이메일 주소를 담은 DTO
     * @return OK 메시지를 담은 200 OK 응답
     */
    @PostMapping("/api/auth/verification/send-code")
    public ResponseEntity<ApiResponse<?>> sendVerificationEmail(@Valid @RequestBody SendCodeRequestDto requestDto) {
        authService.sendVerificationEmail(requestDto.getEmail());
        return ApiResponses.ok();
    }

    /**
     * 이메일과 인증 코드를 검증합니다. (비밀번호 재설정 시 사용)
     *
     * @param requestDto 검증할 이메일과 인증 코드를 담은 DTO
     * @return OK 메시지를 담은 200 OK 응답
     */
    @PostMapping("/api/auth/verification/verify-code")
    public ResponseEntity<ApiResponse<?>> verifyPasswordResetVerification(@Valid @RequestBody VerifyCodeRequestDto requestDto) {
        authService.verifyCodeForPasswordReset(requestDto.getEmail(), requestDto.getAuthKey());
        return ApiResponses.ok();
    }

    /**
     * 이메일과 인증 코드를 검증합니다. (회원가입 시 사용)
     *
     * @param requestDto 검증할 이메일과 인증 코드를 담은 DTO
     * @return OK 메시지를 담은 200 OK 응답
     */
    @PostMapping("/api/auth/signup/verify-code")
    public ResponseEntity<ApiResponse<?>> verifySignupVerification(@Valid @RequestBody VerifyCodeRequestDto requestDto) {
        authService.verifyCodeForSignup(requestDto.getEmail(), requestDto.getAuthKey());
        return ApiResponses.ok();
    }

    /**
     * 인증된 이메일과 새 비밀번호로 비밀번호를 재설정합니다.
     *
     * @param requestDto 이메일, 인증 코드, 새 비밀번호를 담은 DTO
     * @return 본문 없는 204 No Content 성공 응답
     */
    @PostMapping("/api/auth/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto requestDto) {
        authService.resetPassword(requestDto.getEmail(), requestDto.getAuthKey(), requestDto.getPassword());
        return ApiResponses.noContent();
    }

    /**
     * 인증된 이메일과 정보로 회원가입을 진행합니다.
     *
     * @param requestDto 이메일 인증 정보와 가입 정보를 담은 DTO
     * @return Member 엔티티를 ApiResponse.data에 담은 201 Created 성공 응답
     */
    @PostMapping("/api/auth/signup")
    public ResponseEntity<ApiResponse<?>> signup(@Valid @RequestBody SignupRequestDto requestDto) {
        Member registeredMember = authService.register(requestDto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(registeredMember.getId())
                .toUri();
        return ApiResponses.created(location, registeredMember);
    }

    /**
     * 입력된 닉네임이 사용 가능한지 확인합니다.
     *
     * @param requestDto 회원가입을 진행할 닉네임
     * @return 성공 메시지를 담은 200 OK 응답
     */
    @PostMapping("/api/auth/validate-nickname")
    public ResponseEntity<ApiResponse<?>> checkNickname(
            @Validated @RequestBody NicknameValidationRequestDto requestDto) {
        authService.validateDuplicateNickname(requestDto.getNickname());
        return ApiResponses.ok("사용 가능한 닉네임입니다.", null);
    }

    /**
     * 약관 md 파일을 약관 이름으로 가져옵니다.
     *
     * @param type 약관 이름
     * @return 약관 텍스트를 ApiResponse.data에 담은 200 OK 성공 응답
     */
    @GetMapping("/api/auth/terms/{type}")
    public ResponseEntity<ApiResponse<?>> getTerm(@PathVariable String type) {
        return ApiResponses.ok(authService.getTermAsRawText(type));
    }
}
