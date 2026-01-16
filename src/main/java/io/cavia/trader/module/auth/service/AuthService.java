package io.cavia.trader.module.auth.service;

import io.cavia.trader.module.auth.dto.SignupRequestDto;
import io.cavia.trader.module.auth.dto.TokenDto;
import io.cavia.trader.module.member.entity.Member;

public interface AuthService {

    /**
     * 지정된 이메일 주소로 6자리 정수인 인증 코드가 담긴 메일을 발송합니다.
     *
     * @param email 인증 코드를 받을 이메일
     */
    void sendVerificationEmail(String email);

    /**
     * 비밀번호 재설정을 위해 이메일과 인증 코드를 검증합니다.
     *
     * @param email   사용자 이메일
     * @param authKey 사용자가 입력한 인증 코드
     */
    void verifyCodeForPasswordReset(String email, String authKey);

    /**
     * 회원가입을 위해 이메일과 인증 코드를 검증합니다.
     *
     * @param email   사용자 이메일
     * @param authKey 사용자가 입력한 인증 코드
     */
    void verifyCodeForSignup(String email, String authKey);

    /**
     * 사용하려는 닉네임이 이미 존재하는지 검사합니다.
     *
     * @param nickname 중복 검사할 닉네임
     * @throws io.cavia.trader.common.exception.ApiException 닉네임이 이미 존재할 경우
     */
    void validateDuplicateNickname(String nickname);

    /**
     * 전달된 DTO의 정보로 최종 회원가입을 처리하고, 생성된 회원 정보를 반환합니다.
     *
     * @param signupRequestDto 회원가입에 필요한 모든 정보가 담긴 DTO
     * @return 생성된 Member 엔티티
     */
    Member register(SignupRequestDto signupRequestDto);

    /**
     * 사용자의 이메일과 비밀번호를 받아 로그인을 수행합니다.
     *
     * @param email     로그인 시도 이메일
     * @param password  로그인 시도 비밀번호
     * @param userAgent 클라이언트 User-Agent
     * @return 로그인 성공 시 발급된 JWT와 리프레시 토큰이 담겨있는 TokenDto
     * @throws io.cavia.trader.common.exception.ApiException 로그인 실패 시
     */
    TokenDto login(String email, String password, String userAgent);

    /**
     * 이메일과 인증 코드로 사용자를 검증한 후, 새 비밀번호로 재설정합니다.
     *
     * @param email       사용자 이메일
     * @param authKey     이메일로 발급된 인증 코드
     * @param rawPassword 암호화되지 않은 새 비밀번호
     */
    void resetPassword(String email, String authKey, String rawPassword);

    /**
     * 약관 파일 이름으로 약관 파일의 text 그대로 반환합니다.
     *
     * @param type 약관 파일 이름 문자열
     */
    String getTermAsRawText(String type);
}
