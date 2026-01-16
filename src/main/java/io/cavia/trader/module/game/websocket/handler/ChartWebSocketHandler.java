package io.cavia.trader.module.game.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cavia.trader.module.client.dto.QuotesOutput;
import io.cavia.trader.module.client.dto.TradesOutput;
import io.cavia.trader.module.game.dto.GameDto;
import io.cavia.trader.module.game.dto.OrderTableDto;
import io.cavia.trader.module.game.dto.TradeLog;
import io.cavia.trader.module.game.dto.response.ResponseDto;
import io.cavia.trader.module.game.service.GameManager;
import io.cavia.trader.module.auth.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChartWebSocketHandler implements WebSocketHandler {

    private final GameManager gameManager;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Value("${stock.default-fee-rate}")
    private BigDecimal DEFAULT_FEE_RATE;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    }

    @Override
    public void handleMessage(WebSocketSession chartSession, WebSocketMessage<?> message) throws Exception {
        GameDto gameDto;
        String token = message.getPayload().toString();
        if (jwtUtil.validateToken(token)) {

            // 유저가 연결되었을 때, 가장 젊은 게임 세션에 연결 (유저가 이미 세션에 속해 있다면 DTO의 세션만 교체)(락 필요)
            gameDto = gameManager.addChartSessionToGameAndGetYoungestSession(
                    jwtUtil.getUserInfoFromToken(token), chartSession);

            long memberId = gameManager.getUserInfo(jwtUtil.getUserInfoFromToken(token)).getId();
            // 게임 입장 처리 완료, 자신이 포함된 게임 참가 인원 수 게임에 참여중인 모든 세션에 전달

            gameDto.getChartSessions().values().forEach(s -> {
                try {
                    synchronized (s) {
                        if (s.isOpen()) s.sendMessage(new TextMessage("numberOfParticipation||"
                                + gameDto.getPlayerStatusDtos().size()));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("유저 참여 변동 사항 멀티캐스트 중 예외 발생!", e);
                }
            });


            // 해당 게임 세션에 할당된 집계 데이터를 순차적으로 웹소켓으로 전송
            // TODO 중간에 난입한 유저일 경우 집계테이블에서 이미 지난 부분을 집합으로 먼저 전송하고 나머지 집계테이블을 보내야함
            try {

                // 유저에게 전송해야 할 집계 테이블 인덱스 구하기
                int tradesIdx = gameManager.getTradesIndexByLateTime(gameDto.getStartedAt(), gameDto.getTrades());

                // 인덱스 0부터 tradesIdx까지만 새로운 list로 생성 후 먼저 전송
                List<TradesOutput> previewersTrades = IntStream
                        .range(0, tradesIdx)
                        .mapToObj(gameDto.getTrades()::get)
                        .toList();
                // JAVA16부터는 콜렉터로 래핑할 필요 없이 그냥 toList() 사용 가능!!

                String previewersTradesJson = objectMapper.writeValueAsString(previewersTrades);

                synchronized (chartSession) {
                    if (chartSession.isOpen())
                        chartSession.sendMessage(new TextMessage("previewersTrades||" + previewersTradesJson));
                }


                // 새로운 시간 계산법
                // 로직 세션 시작 시간에 타임 존만 추가해서 클라이언트에게 던져주고 시간 계산은 클라이언트에게 넘겨버리기
                List<Object> timeList = new ArrayList<>();
                timeList.add(gameDto.getStartedAt().atZone(ZoneId.systemDefault()));
                timeList.add(LocalDateTime.now().atZone(ZoneId.systemDefault()));

                synchronized (chartSession) {
                    if (chartSession.isOpen())
                        chartSession.sendMessage(new TextMessage("timeLeft||" +
                                objectMapper.writeValueAsString(timeList)));
                }


                AtomicLong stockBaseTime = new AtomicLong(
                        gameDto.getTrades()
                                .get(tradesIdx)
                                .getCreatedAt()
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                );

                Thread thread1 = new Thread(() -> {
                    List<TradesOutput> trades = gameDto.getTrades();
                    for (int i = tradesIdx; i < trades.size(); i++) {
                        try {
                            if (!chartSession.isOpen()) return;
                            long relTime = trades
                                    .get(i)
                                    .getCreatedAt()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli();

                            long timeDifference = relTime - stockBaseTime.get();
                            stockBaseTime.set(relTime);
                            Thread.sleep(timeDifference);

                            //TODO 체결가가 바뀌는 시점. 사용자 거래 요청 목록을 보고 체결가와 맞는 항목의 거래를 발생시킨다
                            gameDto.setCurrentPrice(trades.get(i).getStckPrpr());

                            AtomicInteger tradeIndex = new AtomicInteger(i);

                            gameDto.getPlayerStatusDtos().values().forEach(playerStatusDto -> {
                                // 매번 전부 돌긴 그러니까 확인할 필요가 없는 상황이면 순회를 하지 않도록 해야 함
                                // 언제 확인이 필요 없을까?
                                // 매도 주문이 있을 때, 매수 주문이 있을 때, 시장가 매도 주문이 있을 때, 시장가 매수 주문이 있을 때
                                // 주문 자체를 검증하는 건 service계층에서

                                WebSocketSession orderSession = gameDto.getChartSessions().get(playerStatusDto.getMemberId());

                                Queue<OrderTableDto> Deals = playerStatusDto.getOrderDto().getOrderTableDtos();

                                int quantityOfMarketSell = playerStatusDto.getOrderDto().getQuantityOfMarketSell();
                                int quantityOfMarketBuy = playerStatusDto.getOrderDto().getQuantityOfMarketBuy();

                                // 같은 조건으로 두 번 비교하는 이유
                                // and 조건 불만족시 점프
                                // 각각 조건의 만족할 때만 컬렉션 내부 순회
                                // 수정은 각각의 조건문 내부에서 메세지 전송은 1회만

                                if (!Deals.isEmpty() || quantityOfMarketSell != 0 || quantityOfMarketBuy != 0 || playerStatusDto.isUpdated()) {

                                    AtomicBoolean isTraded = new AtomicBoolean(false);
                                    if (!Deals.isEmpty()) {
                                        // 매수, 매도 음수 양수로 구분하여 체결가와 주문가 비교하여 거래 발생시키기(미체결 거래가 한 테이블에 모여있어야 해서 수정함)
                                        Deals.forEach(((orderTableDto) -> {
                                            if (orderTableDto.getPrice() < 0) {
                                                if (trades.get(tradeIndex.get()).getStckPrpr() >= Math.abs(orderTableDto.getPrice())) {

                                                    if (playerStatusDto.getStocksHolding() < orderTableDto.getQuantity()) {
                                                        synchronized (orderSession) {
                                                            try {
                                                                if (orderSession.isOpen()) {
                                                                    orderSession.sendMessage(
                                                                            new TextMessage("error||" +
                                                                                    objectMapper.writeValueAsString(
                                                                                            new ResponseDto("매도: 보유 주식이 부족합니다."))
                                                                            ));
                                                                }
                                                            }catch (Exception e){
                                                                throw new RuntimeException("예외 메세지 전송 중 예외 발생!", e);
                                                            }
                                                        }
                                                    } else {
                                                        playerStatusDto.setStocksHolding(
                                                                playerStatusDto.getStocksHolding() - orderTableDto.getQuantity());

                                                        // 보유 자산 변동
                                                        // 가능한 안전하게 계산해봄
                                                        long tradeValue = (long) orderTableDto.getQuantity() * trades.get(tradeIndex.get()).getStckPrpr();
                                                        BigDecimal tradeValueAmount = new BigDecimal(tradeValue);
                                                        BigDecimal feeValue = tradeValueAmount.multiply(DEFAULT_FEE_RATE);
                                                        tradeValue = tradeValueAmount.subtract(feeValue).setScale(0, RoundingMode.HALF_UP).intValue();

                                                        log.debug("매도 전: {}", playerStatusDto.getEarnedCash());
                                                        playerStatusDto.setEarnedCash(
                                                                playerStatusDto.getEarnedCash() + tradeValue
                                                        );
                                                        log.debug("매도 후: {}", playerStatusDto.getEarnedCash());

                                                        // 로그 추가
                                                        Queue<TradeLog> tradeLog = playerStatusDto.getOrderDto().getTradeLogs();
                                                        tradeLog.add(
                                                                TradeLog.builder()
                                                                        .Id(orderTableDto.getId())
                                                                        .price(-Math.abs(trades.get(tradeIndex.get()).getStckPrpr()))
                                                                        .quantity(orderTableDto.getQuantity())
                                                                        .createdAt(LocalDateTime.now())
                                                                        .build()
                                                        );

                                                        // 매도 거래 삭제
                                                        Deals.remove(orderTableDto);
                                                        isTraded.set(true);
                                                    }
                                                }
                                            } else {
                                                if (trades.get(tradeIndex.get()).getStckPrpr() <= Math.abs(orderTableDto.getPrice())) {

                                                    if (playerStatusDto.getEarnedCash() < (long) orderTableDto.getQuantity() * orderTableDto.getPrice()) {
                                                        synchronized (orderSession) {
                                                            try {
                                                                if (orderSession.isOpen()) {
                                                                    orderSession.sendMessage(
                                                                            new TextMessage("error||" +
                                                                                    objectMapper.writeValueAsString(
                                                                                            new ResponseDto("매도 실패: 보유 자산이 부족합니다."))
                                                                            ));
                                                                }
                                                            }catch (Exception e){
                                                                throw new RuntimeException("예외 메세지 전송 중 예외 발생!", e);
                                                            }
                                                        }
                                                    } else {

                                                        playerStatusDto.setStocksHolding(
                                                                playerStatusDto.getStocksHolding() + orderTableDto.getQuantity());

                                                        // 보유 자산 변동
                                                        log.debug("매수 전: {}", playerStatusDto.getEarnedCash());
                                                        playerStatusDto.setEarnedCash(
                                                                playerStatusDto.getEarnedCash() -
                                                                        (long) orderTableDto.getQuantity() * trades.get(tradeIndex.get()).getStckPrpr()
                                                        );
                                                        log.debug("매수 후: {}", playerStatusDto.getEarnedCash());

                                                        // 로그 추가
                                                        Queue<TradeLog> tradeLog = playerStatusDto.getOrderDto().getTradeLogs();
                                                        tradeLog.add(
                                                                TradeLog.builder()
                                                                        .Id(orderTableDto.getId())
                                                                        .price(trades.get(tradeIndex.get()).getStckPrpr())
                                                                        .quantity(orderTableDto.getQuantity())
                                                                        .createdAt(LocalDateTime.now())
                                                                        .build()
                                                        );

                                                        // 매수 거래 삭제
                                                        Deals.remove(orderTableDto);
                                                        isTraded.set(true);
                                                    }
                                                }
                                            }

                                        }));

                                    }

                                    // 시장가 매도주문이 존재할 경우 현재 체결가로 주문 처리
                                    if (quantityOfMarketSell != 0) {

                                        if (playerStatusDto.getStocksHolding() < quantityOfMarketSell) {
                                            synchronized (orderSession) {
                                                try {
                                                    if (orderSession.isOpen()) {
                                                        orderSession.sendMessage(
                                                                new TextMessage("error||" +
                                                                        objectMapper.writeValueAsString(
                                                                                new ResponseDto("시장가 매도 실패: 보유 주식이 부족합니다."))
                                                                ));
                                                    }
                                                }catch (Exception e){
                                                    throw new RuntimeException("예외 메세지 전송 중 예외 발생!", e);
                                                }
                                            }
                                        } else {
                                            playerStatusDto.setStocksHolding(
                                                    playerStatusDto.getStocksHolding() - quantityOfMarketSell
                                            );

                                            long tradeValue = (long) quantityOfMarketSell * trades.get(tradeIndex.get()).getStckPrpr();
                                            BigDecimal tradeValueAmount = new BigDecimal(tradeValue);
                                            BigDecimal feeValue = tradeValueAmount.multiply(DEFAULT_FEE_RATE);
                                            tradeValue = tradeValueAmount.subtract(feeValue).setScale(0, RoundingMode.HALF_UP).intValue();

                                            log.debug("시장가 매도 전: {}", playerStatusDto.getEarnedCash());
                                            playerStatusDto.setEarnedCash(
                                                    playerStatusDto.getEarnedCash() + tradeValue
                                            );
                                            log.debug("시장가 매도 후: {}", playerStatusDto.getEarnedCash());

                                            playerStatusDto.getOrderDto().setQuantityOfMarketSell(0);


                                            // 로그 추가 해야겠지?
                                            Queue<TradeLog> tradeLog = playerStatusDto.getOrderDto().getTradeLogs();
                                            tradeLog.add(
                                                    TradeLog.builder()
                                                            .Id(String.format("%06d", playerStatusDto.getIdCreator().getAndIncrement() % 1000000))
                                                            .price(-Math.abs(trades.get(tradeIndex.get()).getStckPrpr()))
                                                            .quantity(quantityOfMarketSell)
                                                            .createdAt(LocalDateTime.now())
                                                            .build()
                                            );
                                            isTraded.set(true);
                                        }
                                    }

                                    // 시장가 매수주문이 존재할 경우 현재 체결가로 주문 처리
                                    if (quantityOfMarketBuy != 0) {
                                        if (quantityOfMarketBuy * (long) trades.get(tradeIndex.get()).getStckPrpr() > playerStatusDto.getEarnedCash()) {
                                            synchronized (orderSession) {
                                                try {
                                                    if (orderSession.isOpen()) {
                                                        orderSession.sendMessage(
                                                                new TextMessage("error||" +
                                                                        objectMapper.writeValueAsString(
                                                                                new ResponseDto("시장가 매수 실패: 보유 자산이 부족합니다."))
                                                                ));
                                                    }
                                                }catch (Exception e){
                                                    throw new RuntimeException("예외 메세지 전송 중 예외 발생!", e);
                                                }
                                            }
                                        } else {
                                            playerStatusDto.setStocksHolding(
                                                    playerStatusDto.getStocksHolding() + quantityOfMarketBuy
                                            );
                                            log.debug("시장가 매수 전: {}", playerStatusDto.getEarnedCash());
                                            playerStatusDto.setEarnedCash(
                                                    playerStatusDto.getEarnedCash() - quantityOfMarketBuy * (long) trades.get(tradeIndex.get()).getStckPrpr()
                                            );
                                            log.debug("시장가 매수 후: {}", playerStatusDto.getEarnedCash());

                                            playerStatusDto.getOrderDto().setQuantityOfMarketBuy(0);

                                            // 로그 추가 해야겠지?
                                            Queue<TradeLog> tradeLog = playerStatusDto.getOrderDto().getTradeLogs();
                                            tradeLog.add(
                                                    TradeLog.builder()
                                                            .Id(String.format("%06d", playerStatusDto.getIdCreator().getAndIncrement() % 1000000))
                                                            .price(trades.get(tradeIndex.get()).getStckPrpr())
                                                            .quantity(quantityOfMarketBuy)
                                                            .createdAt(LocalDateTime.now())
                                                            .build()
                                            );
                                            isTraded.set(true);
                                        }
                                    }
                                    // 거래가 발생했으면 새로고침 된 데이터를 전송
                                    if (playerStatusDto.isUpdated() && isTraded.get()) {
                                        try {
                                            String playerStatusJson = objectMapper.writeValueAsString(playerStatusDto);
                                            synchronized (orderSession) {

                                                if (orderSession.isOpen()) {
                                                    orderSession
                                                            .sendMessage(
                                                                    new TextMessage("playerStatus||" + playerStatusJson
                                                                    )
                                                            );
                                                }
                                            }
                                            playerStatusDto.setUpdated(false);
                                        } catch (Exception e) {
                                            throw new RuntimeException("유저 거래 변동 사항 멀티캐스트 중 예외 발생!", e);
                                        }
                                    }
                                }


                            });

                            String tradesJson = objectMapper.writeValueAsString(trades.get(i));
                            // 메시지 전송 부분 동기화
                            synchronized (chartSession) {
                                if (chartSession.isOpen())
                                    chartSession.sendMessage(new TextMessage("trades||" + tradesJson));
                            }
                        } catch (Exception e2) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e2);
                        }
                    }
                });

                int quotesIdx = gameManager.getQuotesIndexByLateTime(gameDto.getStartedAt(), gameDto.getQuotes());

                List<QuotesOutput> previewersQuotes = IntStream
                        .range(0, quotesIdx)
                        .mapToObj(gameDto.getQuotes()::get)
                        .toList();
                String previewersQuotesJson = objectMapper.writeValueAsString(previewersQuotes);

                if (chartSession.isOpen())
                    chartSession.sendMessage(new TextMessage("previewersQuotes||" + previewersQuotesJson));

                AtomicLong orderBaseTime = new AtomicLong(
                        gameDto.getQuotes()
                                .get(quotesIdx)
                                .getCreatedAt()
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                );

                Thread thread2 = new Thread(() -> {
                    try {
                        List<QuotesOutput> quotes = gameDto.getQuotes();
                        for (int i = quotesIdx; i < quotes.size(); i++) {
                            if (!chartSession.isOpen()) return;
                            long relTime = quotes
                                    .get(i)
                                    .getCreatedAt()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli();

                            long timeDifference = relTime - orderBaseTime.get();
                            orderBaseTime.set(relTime);
                            Thread.sleep(timeDifference);


                            String quotesJson = objectMapper.writeValueAsString(quotes.get(i));

                            // 메시지 전송 부분 동기화
                            synchronized (chartSession) {
                                if (chartSession.isOpen()) {
                                    chartSession.sendMessage(new TextMessage("quotes||" + quotesJson));
                                }
                            }
                        }

                    } catch (Exception e2) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e2);
                    }

                });

                thread1.start();
                thread2.start();

            } catch (Exception e3) {
                throw new RuntimeException(e3);
            }
        } else {
            chartSession.sendMessage(new TextMessage("유효하지 않은 JWT 토큰입니다."));
            throw new RuntimeException("유효하지 않은 JWT 토큰을 가진 사용자가 게임 입장을 시도하였습니다 sessionID:" + chartSession.getId());
        }

    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        throw new RuntimeException("웹소켓 통신 중 예외 발생: " + exception.getMessage(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        // 유저가 웹소켓 연결을 종료하면 세션을 종료하고 유저가 속한 세션을 찾아서 삭제
        gameManager.removeChartSession(session);
        session.close();
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
