package com.dorazibe02.imap.Notion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotionRepository extends JpaRepository<Notion, Long> {
    Optional<Notion> findByAuthId(Long authId);
}
