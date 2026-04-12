package com.instabond.scheduler;

import com.instabond.entity.Relationship;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelationshipScheduler {

    private final MongoTemplate mongoTemplate;

    @Scheduled(cron = "0 0 0 * * *")
    public void processDailyRelationship() {
        log.info("[RelationshipScheduler] Initiating daily maintenance job for Streaks and Intimacy Scores...");

        Instant now = Instant.now();
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant startOfYesterday = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant();
        Instant sevenDaysAgo = LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant();

        // ========================================================================
        // DEAD STREAK CLEANUP
        // Condition: Relationships with an active streak (>0) but no interaction yesterday.
        // -> Reset streak counters and states to default.
        // ========================================================================
        Query killStreakQuery = new Query(new Criteria().andOperator(
                Criteria.where("streak.last_interaction_date").lt(startOfYesterday),
                Criteria.where("streak.count").gt(0)
        ));

        Update killStreakUpdate = new Update()
                .set("streak.count", 0)
                .set("streak.has_fired_streak", false)
                .set("streak.is_pending_reply", false)
                .set("updated_at", now);

        var deadStreakResult = mongoTemplate.updateMulti(killStreakQuery, killStreakUpdate, Relationship.class);
        log.info("[RelationshipScheduler] Dead streaks cleaned up. Modified records: {}", deadStreakResult.getModifiedCount());

        // ========================================================================
        // INTIMACY SCORE DECAY
        // Condition: Relationships inactive for 7 or more days.
        // -> Deduct 10 points per day, flooring the score at 0.
        // ========================================================================
        Query queryGt10 = new Query(new Criteria().andOperator(
                Criteria.where("last_interaction_at").lt(sevenDaysAgo),
                Criteria.where("intimacy_score").gt(10)
        ));
        var resultGt10 = mongoTemplate.updateMulti(queryGt10, new Update().inc("intimacy_score", -10).set("updated_at", now), Relationship.class);

        Query queryLt10 = new Query(new Criteria().andOperator(
                Criteria.where("last_interaction_at").lt(sevenDaysAgo),
                Criteria.where("intimacy_score").gt(0),
                Criteria.where("intimacy_score").lte(10)
        ));
        var resultLt10 = mongoTemplate.updateMulti(queryLt10, new Update().set("intimacy_score", 0).set("updated_at", now), Relationship.class);

        long totalAffected = resultGt10.getModifiedCount() + resultLt10.getModifiedCount();
        log.info("[RelationshipScheduler] Score decay applied. Total relationships affected: {} (Reduced: {}, Reset to 0: {})",
                totalAffected, resultGt10.getModifiedCount(), resultLt10.getModifiedCount());

        // ========================================================================
        // FRIENDSHIP LEVEL SYNCHRONIZATION
        // ========================================================================

        // Normal (0 - 100)
        Query queryNormal = new Query(new Criteria().andOperator(
                Criteria.where("intimacy_score").lte(100),
                Criteria.where("friendship_level").ne("normal")
        ));
        var resultNormal = mongoTemplate.updateMulti(queryNormal, new Update().set("friendship_level", "normal").set("updated_at", now), Relationship.class);

        // Close friends (101 - 500)
        Query queryCloseFriends = new Query(new Criteria().andOperator(
                Criteria.where("intimacy_score").gt(100).lte(500),
                Criteria.where("friendship_level").ne("close friends")
        ));
        var resultCloseFriends = mongoTemplate.updateMulti(queryCloseFriends, new Update().set("friendship_level", "close friends").set("updated_at", now), Relationship.class);

        // Besties (501 - 2000)
        Query queryBesties = new Query(new Criteria().andOperator(
                Criteria.where("intimacy_score").gt(500).lte(2000),
                Criteria.where("friendship_level").ne("besties")
        ));
        var resultBesties = mongoTemplate.updateMulti(queryBesties, new Update().set("friendship_level", "besties").set("updated_at", now), Relationship.class);

        log.info("[RelationshipScheduler] Friendship levels synchronized. Normal: {}, Close friends: {}, Besties: {}",
                resultNormal.getModifiedCount(), resultCloseFriends.getModifiedCount(), resultBesties.getModifiedCount());
    }
}