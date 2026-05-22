package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, String> {
    Optional<ApiToken> findByTokenHashAndRevokedFalse(String tokenHash);
    void deleteByUserIdAndId(String userId, String id);
}
