package com.cognition.devinops.repo;

import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.domain.StateTransition;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateTransitionRepository extends JpaRepository<StateTransition, Long> {

    long countByToStateAndOccurredAtGreaterThanEqual(RemediationState toState, Instant since);
}
