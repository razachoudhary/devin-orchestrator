package com.cognition.devinops.orchestration;

public record GateDecision(boolean allowed, String reason) {

    public static GateDecision allow() {
        return new GateDecision(true, null);
    }

    public static GateDecision reject(String reason) {
        return new GateDecision(false, reason);
    }
}
