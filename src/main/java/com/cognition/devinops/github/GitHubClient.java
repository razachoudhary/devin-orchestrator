package com.cognition.devinops.github;

public interface GitHubClient {

    long createComment(int issueNumber, String body);

    void updateComment(long commentId, String body);

    void addLabel(int issueNumber, String label);

    void removeLabel(int issueNumber, String label);

    String getCheckRunLogs(long checkRunId);
}
