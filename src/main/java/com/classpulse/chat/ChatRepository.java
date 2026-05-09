package com.classpulse.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.session.id = :sessionId ORDER BY m.sentAt DESC LIMIT :limit")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") UUID sessionId, @Param("limit") int limit);

    @Query("""
            SELECT m FROM ChatMessage m JOIN FETCH m.sender
            WHERE m.session.id = :sessionId
              AND m.sentAt < (SELECT c.sentAt FROM ChatMessage c WHERE c.id = :beforeId)
            ORDER BY m.sentAt DESC
            LIMIT :limit
            """)
    List<ChatMessage> findBeforeBySessionId(@Param("sessionId") UUID sessionId,
                                            @Param("beforeId") UUID beforeId,
                                            @Param("limit") int limit);
}
