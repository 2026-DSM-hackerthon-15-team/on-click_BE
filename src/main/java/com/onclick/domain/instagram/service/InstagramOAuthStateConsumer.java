package com.onclick.domain.instagram.service;

import java.time.Clock;

import com.onclick.domain.instagram.entity.InstagramOAuthState;
import com.onclick.domain.instagram.repository.InstagramOAuthStateRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstagramOAuthStateConsumer {

    private final InstagramOAuthStateRepository repository;
    private final Clock clock;

    public InstagramOAuthStateConsumer(InstagramOAuthStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsumedState consume(String stateHash) {
        InstagramOAuthState state = repository.findByStateHash(stateHash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INSTAGRAM_OAUTH_STATE));
        try {
            state.consume(clock.instant());
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.INVALID_INSTAGRAM_OAUTH_STATE);
        }
        return new ConsumedState(state.getStoreId(), state.getUserId());
    }

    public record ConsumedState(Long storeId, Long userId) {
    }
}
