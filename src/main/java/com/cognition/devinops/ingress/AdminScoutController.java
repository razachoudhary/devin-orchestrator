package com.cognition.devinops.ingress;

import com.cognition.devinops.config.DevinProperties;
import com.cognition.devinops.config.GitHubProperties;
import com.cognition.devinops.devin.DevinClient;
import com.cognition.devinops.devin.dto.CreateSessionRequest;
import com.cognition.devinops.devin.dto.DevinSession;
import com.cognition.devinops.domain.FindingSource;
import com.cognition.devinops.domain.Remediation;
import com.cognition.devinops.domain.RemediationState;
import com.cognition.devinops.repo.RemediationRepository;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminScoutController {

    private final RemediationRepository remediations;
    private final DevinClient devinClient;
    private final DevinProperties devinProperties;
    private final GitHubProperties gitHubProperties;

    public AdminScoutController(RemediationRepository remediations, DevinClient devinClient,
                                DevinProperties devinProperties, GitHubProperties gitHubProperties) {
        this.remediations = remediations;
        this.devinClient = devinClient;
        this.devinProperties = devinProperties;
        this.gitHubProperties = gitHubProperties;
    }

    public record ScoutResponse(String sessionId, String sessionUrl) {
    }

    @PostMapping("/admin/scout")
    @Transactional
    public ResponseEntity<?> scout() {
        Remediation remediation = Remediation.receive(0, "scout: maintenance debt scan", FindingSource.SCOUT);
        try {
            remediation = remediations.saveAndFlush(remediation);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "a scout run is already live"));
        }
        DevinSession session = devinClient.createSession(new CreateSessionRequest(
                scoutPrompt(),
                blankToNull(devinProperties.scoutPlaybookId()),
                blankToNull(devinProperties.forkKnowledgeId()) == null
                        ? null : List.of(devinProperties.forkKnowledgeId()),
                devinProperties.maxAcuLimit(),
                null,
                null,
                devinProperties.tagsEnabled() ? List.of("devinops", "scout") : null));
        remediation.recordSession(session.sessionId(), session.url());
        remediation.transitionTo(RemediationState.SESSION_RUNNING,
                "scout session %s dispatched".formatted(session.sessionId()));
        remediations.save(remediation);
        return ResponseEntity.accepted().body(new ScoutResponse(session.sessionId(), session.url()));
    }

    private String scoutPrompt() {
        return """
                Scan %s for maintenance debt following the scout playbook: find recent bug-fix
                commits that lack regression tests. File at most 3 GitHub issues, each labeled
                devin-fix, containing the commit reference and a short description of the missing
                coverage. Do not modify any code and do not open pull requests.
                """.formatted(gitHubProperties.repo());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
