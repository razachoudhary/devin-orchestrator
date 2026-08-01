package com.cognition.devinops.devin;

import com.cognition.devinops.devin.dto.CreateSessionRequest;
import com.cognition.devinops.devin.dto.DevinSelf;
import com.cognition.devinops.devin.dto.DevinSession;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Profile("simulate")
public class ReplayDevinClient implements DevinClient {

    private static final List<String> SCENARIOS = List.of("happy-path", "repair-loop", "escalation", "blocked");
    private static final String DEFAULT_SCENARIO = "happy-path";

    private final Map<String, List<TimelineEntry>> timelines = new HashMap<>();
    private final Map<String, ReplaySession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final Clock clock;

    @Autowired
    public ReplayDevinClient(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    public ReplayDevinClient(ObjectMapper objectMapper, Clock clock) {
        this.clock = clock;
        for (String scenario : SCENARIOS) {
            timelines.put(scenario, loadTimeline(objectMapper, scenario));
        }
    }

    private static List<TimelineEntry> loadTimeline(ObjectMapper objectMapper, String scenario) {
        try (InputStream in = new ClassPathResource("fixtures/sessions/" + scenario + ".json").getInputStream()) {
            List<TimelineEntry> timeline = objectMapper.readValue(in, new TypeReference<List<TimelineEntry>>() { });
            if (timeline.isEmpty()) {
                throw new IllegalStateException("fixture " + scenario + " has no entries");
            }
            return timeline;
        } catch (IOException e) {
            throw new IllegalStateException("cannot load fixture " + scenario, e);
        }
    }

    @Override
    public DevinSession createSession(CreateSessionRequest request) {
        String scenario = detectScenario(request.prompt());
        String sessionId = "sim-%s-%d".formatted(scenario, counter.incrementAndGet());
        ReplaySession session = new ReplaySession(scenario, clock.instant());
        sessions.put(sessionId, session);
        return snapshot(sessionId, session, 0);
    }

    @Override
    public DevinSession getSession(String sessionId) {
        ReplaySession session = required(sessionId);
        long elapsedSeconds = Duration.between(session.startedAt(), clock.instant()).toSeconds();
        return snapshot(sessionId, session, elapsedSeconds);
    }

    @Override
    public void sendMessage(String sessionId, String message) {
        ReplaySession session = required(sessionId);
        session.messages().add(message);
        resumeAfterMessage(session);
    }

    private void resumeAfterMessage(ReplaySession session) {
        long elapsedSeconds = Duration.between(session.startedAt(), clock.instant()).toSeconds();
        for (TimelineEntry entry : timelines.get(session.scenario())) {
            if (entry.elapsedSeconds() > elapsedSeconds && "resuming".equals(entry.session().status())) {
                session.setStartedAt(clock.instant().minusSeconds(entry.elapsedSeconds()));
                return;
            }
        }
    }

    @Override
    public DevinSelf whoAmI() {
        return new DevinSelf("sim-service-user", "Replay Devin", "replay@simulate.local");
    }

    public List<String> messagesFor(String sessionId) {
        return List.copyOf(required(sessionId).messages());
    }

    private ReplaySession required(String sessionId) {
        ReplaySession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("unknown session " + sessionId);
        }
        return session;
    }

    private String detectScenario(String prompt) {
        String text = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        return SCENARIOS.stream().filter(text::contains).findFirst().orElse(DEFAULT_SCENARIO);
    }

    private DevinSession snapshot(String sessionId, ReplaySession session, long elapsedSeconds) {
        List<TimelineEntry> timeline = timelines.get(session.scenario());
        DevinSession current = timeline.get(0).session();
        for (TimelineEntry entry : timeline) {
            if (entry.elapsedSeconds() <= elapsedSeconds) {
                current = entry.session();
            }
        }
        return new DevinSession(
                sessionId,
                "https://app.devin.ai/sessions/" + sessionId,
                current.status(),
                current.reason(),
                current.pullRequests(),
                current.acusConsumed(),
                current.structuredOutput());
    }

    record TimelineEntry(
            @JsonProperty("elapsed_seconds") long elapsedSeconds,
            @JsonProperty("session") DevinSession session
    ) {
    }

    private static final class ReplaySession {

        private final String scenario;
        private final List<String> messages = new ArrayList<>();
        private Instant startedAt;

        ReplaySession(String scenario, Instant startedAt) {
            this.scenario = scenario;
            this.startedAt = startedAt;
        }

        String scenario() {
            return scenario;
        }

        Instant startedAt() {
            return startedAt;
        }

        void setStartedAt(Instant startedAt) {
            this.startedAt = startedAt;
        }

        List<String> messages() {
            return messages;
        }
    }
}
