package io.cavia.trader.module.game.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cavia.trader.module.game.dto.ChatLog;
import io.cavia.trader.module.game.dto.GameDto;
import io.cavia.trader.module.game.service.GameManager;
import io.cavia.trader.module.auth.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.time.LocalDateTime;


@RequiredArgsConstructor
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private final GameManager gameManager;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    private GameDto gameDto;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    }

    @Override
    public void handleMessage(WebSocketSession chatSession, WebSocketMessage<?> message) throws Exception {
        // 사용자가 임의로 웹소켓 핸들러를 조작하기 힘들도록 모든 메세지를 json으로 받아서 node로 처리할 것임!
        JsonNode msgNode = objectMapper.readTree(message.getPayload().toString());
            String token = msgNode.get("Authorization").asText();

            // 유효한 토큰인지 검사하고 유효하지 않으면 에러 메세지 보내고 세션 종료
        if (jwtUtil.validateToken(token)) {
            Claims memberClaims = jwtUtil.getUserInfoFromToken(token);
            long memberId = gameManager.getUserInfo(memberClaims).getId();

            if (msgNode.get("type").asText().equals("validate")) {

                // 사용자가 보낸 json에 type필드가 validate면 GameSession에 유저 정보 주입(락 필요)
                gameDto = gameManager.addChatSessionToGameAndGetYoungestSession(
                        memberClaims, chatSession);

            } else if (msgNode.get("type").asText().equals("chat")) {
                // 사용자가 보낸 json에 type필드가 chat이면 해당 유저가 보낸 메세지를 유저가 포함된 ChatSession에 멀티캐스트
                String msg = msgNode.get("message").asText();
                ChatLog chatLog = ChatLog
                        .builder()
                        .memberId(memberId)
                        .memberNickname(gameDto.getPlayerStatusDtos().get(memberId).getMemberNickname())
                        .msg(msg)
                        .sentTime(LocalDateTime.now())
                        .build();

                gameDto.getChatLogs().add(chatLog);
                synchronized (gameDto.getChatLogs()) {
                    gameDto.getChatSessions().values().forEach(s -> {
                        try {
                            s.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatLog)));
                        } catch (Exception e) {
                            throw new RuntimeException("유저 메세지 전송 중 예외 발생!", e);
                        }
                    });
                }

            } else {
                throw new RuntimeException("알 수 없는 타입의 메세지가 수신되었습니다.");
            }
        } else {
            chatSession.sendMessage(new TextMessage("유효하지 않은 JWT 토큰입니다."));
            chatSession.close();
            throw new RuntimeException("유효하지 않은 JWT 토큰을 가진 사용자가 게임 입장을 시도하였습니다 sessionID:" + chatSession.getId());
        }
    }

    @Override
    // WebSocket 통신 중 오류가 발생했을 때 호출됨
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        throw new RuntimeException("WebSocket 통신 중 예외 발생: " + exception.getMessage(), exception);
    }

    @Override
    // 클라이언트가 WebSocket 연결을 끊었을 때 호출됨
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        gameManager.removeChatSession(session);
        session.close();
    }

    @Override
    //클라이언트가 메시지를 여러 조각으로 나눠서 보낼 수 있게 허용할지 여부
    public boolean supportsPartialMessages() {
        return false;
    }
}
