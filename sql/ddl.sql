-- 외래키 제약조건을 잠시 비활성화하여 순서에 상관없이 테이블을 삭제할 수 있도록 합니다.
SET FOREIGN_KEY_CHECKS = 0;

-- 기존 테이블이 존재할 경우 삭제합니다.
DROP TABLE IF EXISTS `game_participations`;
DROP TABLE IF EXISTS `trades`;
DROP TABLE IF EXISTS `quotes`;
DROP TABLE IF EXISTS `games`;
DROP TABLE IF EXISTS `stocks`;
DROP TABLE IF EXISTS `members`;
DROP TABLE IF EXISTS `notices`;
DROP TABLE IF EXISTS `email_verifications`;
DROP TABLE IF EXISTS `posts`;

-- 외래키 제약조건을 다시 활성화합니다.
SET FOREIGN_KEY_CHECKS = 1;


-- =================================================================
--                     테이블 생성 (생성 순서에 맞게 정렬)
-- =================================================================

-- 1. `stocks` 테이블 (다른 테이블에서 참조됨)
CREATE TABLE `stocks` (
   `id`   INT   PRIMARY KEY AUTO_INCREMENT,
   `code`   VARCHAR(9)   NOT NULL,
   `name`   VARCHAR(40)   NOT NULL,
   `created_at`   TIMESTAMP   NOT NULL
);

-- 2. `members` 테이블 (다른 테이블에서 참조됨)
CREATE TABLE `members` (
   `id`   INT   PRIMARY KEY AUTO_INCREMENT,
   `email`   VARCHAR(255)   NOT NULL UNIQUE COLLATE utf8mb4_unicode_ci,
   `password`   VARCHAR(255)   NOT NULL,
   `role`   VARCHAR(20),
   `nickname`   VARCHAR(255)   NOT NULL UNIQUE COLLATE utf8mb4_unicode_ci,
   `cash`   BIGINT   NOT NULL DEFAULT 0,
   `total_score`   INT   NOT NULL DEFAULT 0,
   `created_at`   TIMESTAMP   NOT NULL,
   `nickname_updated_at`   TIMESTAMP   NULL,
   `password_updated_at`   TIMESTAMP   NULL
);

-- 3. `notices` 테이블 (다른 테이블과의 관계 없음)
CREATE TABLE `notices` (
   `id`   INT   PRIMARY KEY AUTO_INCREMENT,
   `title`   VARCHAR(255)  NOT NULL,
   `content`   TEXT   NOT NULL,
   `created_at`   TIMESTAMP   NOT NULL,
   `updated_at`   TIMESTAMP   NULL,
   `pinned`   BOOLEAN   NOT NULL   DEFAULT FALSE
);

-- 4. `games` 테이블 (stocks 테이블을 참조)
CREATE TABLE `games` (
   `id`   INT   PRIMARY KEY AUTO_INCREMENT,
   `stock_id`   INT   NOT NULL,
   `started_at`   TIMESTAMP   NOT NULL
);

-- 5. `trades` 테이블 (stocks 테이블을 참조)
CREATE TABLE `trades` (
   `id`   BIGINT   PRIMARY KEY AUTO_INCREMENT,
   `stock_id`   INT   NOT NULL,
   `execution_price`   INT   NOT NULL,
   `execution_volume`   INT   NOT NULL,
   `vi_trigger_price`   INT   NOT NULL,
   `created_at`   TIMESTAMP   NOT NULL,
   CONSTRAINT `FK_stocks_TO_trades_1` FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`id`)
);

-- 6. `quotes` 테이블 (stocks 테이블을 참조)
CREATE TABLE `quotes` (
   `id`   BIGINT   PRIMARY KEY AUTO_INCREMENT,
   `stock_id`   INT   NOT NULL,
   `ask_price1`   INT   NOT NULL,
   `ask_price2`   INT   NOT NULL,
   `ask_price3`   INT   NOT NULL,
   `ask_price4`   INT   NOT NULL,
   `ask_price5`   INT   NOT NULL,
   `ask_price6`   INT   NOT NULL,
   `ask_price7`   INT   NOT NULL,
   `ask_price8`   INT   NOT NULL,
   `ask_price9`   INT   NOT NULL,
   `ask_price10`   INT   NOT NULL,
   `bid_price1`   INT   NOT NULL,
   `bid_price2`   INT   NOT NULL,
   `bid_price3`   INT   NOT NULL,
   `bid_price4`   INT   NOT NULL,
   `bid_price5`   INT   NOT NULL,
   `bid_price6`   INT   NOT NULL,
   `bid_price7`   INT   NOT NULL,
   `bid_price8`   INT   NOT NULL,
   `bid_price9`   INT   NOT NULL,
   `bid_price10`   INT   NOT NULL,
   `ask_volume1`   BIGINT   NOT NULL,
   `ask_volume2`   BIGINT   NOT NULL,
   `ask_volume3`   BIGINT   NOT NULL,
   `ask_volume4`   BIGINT   NOT NULL,
   `ask_volume5`   BIGINT   NOT NULL,
   `ask_volume6`   BIGINT   NOT NULL,
   `ask_volume7`   BIGINT   NOT NULL,
   `ask_volume8`   BIGINT   NOT NULL,
   `ask_volume9`   BIGINT   NOT NULL,
   `ask_volume10`   BIGINT   NOT NULL,
   `bid_volume1`   BIGINT   NOT NULL,
   `bid_volume2`   BIGINT   NOT NULL,
   `bid_volume3`   BIGINT   NOT NULL,
   `bid_volume4`   BIGINT   NOT NULL,
   `bid_volume5`   BIGINT   NOT NULL,
   `bid_volume6`   BIGINT   NOT NULL,
   `bid_volume7`   BIGINT   NOT NULL,
   `bid_volume8`   BIGINT   NOT NULL,
   `bid_volume9`   BIGINT   NOT NULL,
   `bid_volume10`   BIGINT   NOT NULL,
   `created_at`   TIMESTAMP   NOT NULL,
   CONSTRAINT `FK_stocks_TO_quotes_1` FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`id`)
);

-- 7. `game_participations` 테이블 (members, games 테이블을 참조)
CREATE TABLE `game_participations` (
   `id`   BIGINT   PRIMARY KEY AUTO_INCREMENT,
   `member_id`   INT   NOT NULL,
   `game_id`   INT   NOT NULL,
   `return_rate`   FLOAT   NOT NULL,
   `game_rank`   INT   NOT NULL,
   `earned_score`   INT   NOT NULL,
   `post_score`   INT   NOT NULL,
   `earned_cash`   BIGINT   NOT NULL,
   `post_cash`   BIGINT   NOT NULL,
   `entered_at`   TIMESTAMP   NOT NULL,
   CONSTRAINT `FK_members_TO_game_participations_1` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`) ON DELETE CASCADE,
   CONSTRAINT `FK_games_TO_game_participations_1` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
);

-- 8. `email_verifications` 테이블 (이메일 인증키 관리)
CREATE TABLE `email_verifications` (
   `email`            VARCHAR(255) primary KEY COLLATE utf8mb4_unicode_ci,
   `verification_key` VARCHAR(255) NOT NULL,
   `expires_at`       TIMESTAMP    NOT NULL COMMENT '인증키 만료 시간',
   `created_at`       TIMESTAMP    NOT NULL
);

-- 9. `posts` 테이블 (게시글)
CREATE TABLE posts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content TEXT NOT NULL,
    member_id INT NOT null,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_created_at (created_at DESC),
    FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    member_id INT NOT NULL,
    token_value VARCHAR(255) NOT NULL UNIQUE,
    user_agent VARCHAR(255),
    issued_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
);
