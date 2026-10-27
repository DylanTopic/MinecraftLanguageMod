package io.github.dylantopic.langmod.challenge;

/** The challenge currently shown to one player. Only touched on the server thread. */
final class Challenge {
	/** What the client last reported; the countdown only runs while LISTENING. */
	enum Phase {
		LISTENING,
		RECORDING,
		CHECKING
	}

	final int id;
	final PendingItem item;
	final int maxAttempts;
	int remainingTicks;
	int attemptsUsed;
	Phase phase = Phase.LISTENING;
	/** Server tick at which {@link #phase} last changed; used to recover from a silent client. */
	long phaseSince;

	Challenge(int id, PendingItem item, int timeLimitTicks, int maxAttempts, long now) {
		this.id = id;
		this.item = item;
		this.remainingTicks = timeLimitTicks;
		this.maxAttempts = maxAttempts;
		this.phaseSince = now;
	}

	boolean timerRunning() {
		return phase == Phase.LISTENING;
	}
}
