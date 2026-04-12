package com.instabond.listener;

import com.instabond.entity.Relationship;
import com.instabond.dto.UserInteractionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class InteractionEventListener {

    private final MongoTemplate mongoTemplate;

    @EventListener
    public void handleUserInteraction(UserInteractionDTO event) {
        String senderId = event.getSenderId();
        String receiverId = event.getReceiverId();

        int points = calculatePoints(event.getActionType());
        if (points == 0) return;

        log.info("[InteractionEventListener] Processing interaction: {} -> {} | Action: {}", senderId, receiverId, event.getActionType());

        Query querySender = buildRelationshipQuery(senderId, receiverId);
        Query queryReceiver = buildRelationshipQuery(receiverId, senderId);

        Relationship relSender = mongoTemplate.findOne(querySender, Relationship.class);

        if (relSender != null) {
            Instant now = Instant.now();
            ZoneId zone = ZoneId.systemDefault();

            // Base Score Update
            int newScore = Math.max(0, relSender.getIntimacy_score() + points);
            Update updateSender = new Update().set("intimacy_score", newScore);
            Update updateReceiverStreak = new Update();

            // Streak Logic
            boolean isPositiveAction = points > 0;
            boolean isStreakEligible = isStreakEligible(event.getActionType());
            boolean shouldSyncStreak = isPositiveAction && isStreakEligible;

            if (shouldSyncStreak) {
                applyStreakLogic(relSender, senderId, now, zone, updateSender, updateReceiverStreak);
            }

            // Friendship Level Logic
            applyFriendshipLevelLogic(relSender.getFriendship_level(), newScore, updateSender);

            // Timestamp & Execute
            updateSender.set("updated_at", now);
            updateSender.set("last_interaction_at", now);
            mongoTemplate.updateFirst(querySender, updateSender, Relationship.class);

            if (shouldSyncStreak) {
                updateReceiverStreak.set("updated_at", now);
                updateReceiverStreak.set("last_interaction_at", now);
                mongoTemplate.updateFirst(queryReceiver, updateReceiverStreak, Relationship.class);
            }
        }
    }

    // === HELPERS ===

    private int calculatePoints(String actionType) {
        return switch (actionType) {
            case "LIKE" -> 2;
            case "COMMENT" -> 5;
            case "TAG" -> 30;
            case "CHAT" -> 10;
            case "UNLIKE" -> -2;
            case "DELETE_COMMENT" -> -5;
            default -> 0;
        };
    }

    private boolean isStreakEligible(String actionType) {
        return switch (actionType) {
            case "CHAT", "TAG" -> true;
            default -> false;
        };
    }

    private Query buildRelationshipQuery(String requesterId, String recipientId) {
        return new Query(new Criteria().andOperator(
                Criteria.where("requester_id").is(requesterId),
                Criteria.where("recipient_id").is(recipientId)
        ));
    }

    private void applyFriendshipLevelLogic(String currentLevel, int newScore, Update updateSender) {
        String newLevel = "normal"; // Default tier (0 - 100)
        if (newScore > 2000) {
            newLevel = "soulmates";
        } else if (newScore >= 501) {
            newLevel = "besties";
        } else if (newScore >= 101) {
            newLevel = "close friends";
        }

        if (currentLevel == null || !currentLevel.equals(newLevel)) {
            updateSender.set("friendship_level", newLevel);
            log.info("[InteractionEventListener] Relationship upgraded/downgraded to: {}", newLevel);
        }
    }

    private void applyStreakLogic(Relationship relSender, String senderId, Instant now, ZoneId zone,
                                  Update updateSender, Update updateReceiverStreak) {

        // ---------------------------------------------------------
        // RESOLVE STREAK STATE
        // ---------------------------------------------------------
        boolean shouldReset = false;
        boolean shouldStartNewDay = false;
        boolean shouldIncrement = false;

        if (relSender.getStreak() != null && relSender.getStreak().getLast_interaction_date() != null) {
            Instant lastInteraction = relSender.getStreak().getLast_interaction_date();
            LocalDate lastDate = lastInteraction.atZone(zone).toLocalDate();
            LocalDate nowDate = now.atZone(zone).toLocalDate();
            long daysBetween = ChronoUnit.DAYS.between(lastDate, nowDate);

            String lastSender = relSender.getStreak().getLast_sender_id();
            boolean isPingPong = lastSender == null || !lastSender.equals(senderId);
            Boolean wasPendingYesterday = relSender.getStreak().getIs_pending_reply();

            if (daysBetween >= 2) {
                // Users abandoned the chat for more than a day -> Break streak
                shouldReset = true;
            } else if (daysBetween == 1) {
                if (Boolean.TRUE.equals(wasPendingYesterday)) {
                    // Someone initiated yesterday, but the other ignored it -> Break streak
                    shouldReset = true;
                } else {
                    // Streak was successfully completed yesterday -> Open a new streak day
                    shouldStartNewDay = true;
                }
            } else if (daysBetween == 0) {
                if (isPingPong && Boolean.TRUE.equals(wasPendingYesterday)) {
                    // Ping-Pong completed -> Level up streak
                    shouldIncrement = true;
                }
            }
        } else {
            shouldReset = true;
        }

        // ---------------------------------------------------------
        // APPLY DATABASE UPDATES BASED ON STATE
        // ---------------------------------------------------------
        if (shouldReset) {
            // Start count at 0 | Turn off the fire icon | Wait for the reply
            log.info("[InteractionEventListener] Streak reset for relationship: {} <-> {}", senderId, relSender.getRecipient_id());
            updateSender.set("streak.count", 0)
                    .set("streak.has_fired_streak", false)
                    .set("streak.is_pending_reply", true);

            updateReceiverStreak.set("streak.count", 0)
                    .set("streak.has_fired_streak", false)
                    .set("streak.is_pending_reply", true);

        } else if (shouldStartNewDay) {
            log.info("[InteractionEventListener] Starting a new streak day for relationship: {} <-> {}", senderId, relSender.getRecipient_id());
            // Keep existing count | Wait for the reply
            updateSender.set("streak.is_pending_reply", true);
            updateReceiverStreak.set("streak.is_pending_reply", true);

        } else if (shouldIncrement) {
            log.info("[InteractionEventListener] Incrementing streak for relationship: {} <-> {}", senderId, relSender.getRecipient_id());
            // Increment count | Light up the fire icon | Turn off pending reply
            updateSender.inc("streak.count", 1)
                    .set("streak.has_fired_streak", true)
                    .set("streak.is_pending_reply", false);

            updateReceiverStreak.inc("streak.count", 1)
                    .set("streak.has_fired_streak", true)
                    .set("streak.is_pending_reply", false);
        }

        // ---------------------------------------------------------
        // ALWAYS UPDATE CONTEXT FOR THE NEXT INTERACTION
        // ---------------------------------------------------------
        updateSender.set("streak.last_interaction_date", now)
                .set("streak.last_sender_id", senderId);

        updateReceiverStreak.set("streak.last_interaction_date", now)
                .set("streak.last_sender_id", senderId);
    }
}
