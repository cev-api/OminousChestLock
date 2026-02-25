package com.ominouschestlock.paper;

record PickState(int rustyLimit, int rustyAttempts,
                 int normalLimit, int normalAttempts,
                 int silenceLimit, int silenceAttempts,
                 int silenceOverLimitAttempts, long silencePenaltyTimestamp) {
    static PickState empty() {
        return new PickState(-1, 0, -1, 0, -1, 0, 0, 0L);
    }

    PickState withRustyLimit(int limit) {
        return new PickState(limit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    PickState withRustyAttempts(int attempts) {
        return new PickState(rustyLimit, attempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    PickState withNormalLimit(int limit) {
        return new PickState(rustyLimit, rustyAttempts, limit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    PickState withNormalAttempts(int attempts) {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, attempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    PickState withSilenceLimit(int limit) {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, limit, silenceAttempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    PickState withSilenceAttempts(int attempts) {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, attempts,
                silenceOverLimitAttempts, silencePenaltyTimestamp);
    }

    PickState withSilenceOverLimitAttempts(int attempts) {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                attempts, silencePenaltyTimestamp);
    }

    PickState withSilencePenaltyTimestamp(long timestamp) {
        return new PickState(rustyLimit, rustyAttempts, normalLimit, normalAttempts, silenceLimit, silenceAttempts,
                silenceOverLimitAttempts, timestamp);
    }
}

