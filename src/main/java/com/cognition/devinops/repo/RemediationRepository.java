package com.cognition.devinops.repo;

import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationRepository extends JpaRepository<Remediation, Long> {

    boolean existsByIssueNumberAndStateIn(int issueNumber, Collection<RemediationState> states);

    Optional<Remediation> findFirstByPrNumberAndStateInOrderByIdDesc(int prNumber, Collection<RemediationState> states);

    List<Remediation> findByStateIn(Collection<RemediationState> states);
}
