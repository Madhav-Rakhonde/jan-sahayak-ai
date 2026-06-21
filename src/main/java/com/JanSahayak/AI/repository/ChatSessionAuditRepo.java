package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.ChatSessionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionAuditRepo extends JpaRepository<ChatSessionAudit, Long> {
    Optional<ChatSessionAudit> findBySessionId(String sessionId);
}
