package io.github.dylantopic.langmod.core.talk;

/**
 * What a villager knows when it answers: who it is talking to and the world around it.
 *
 * @param playerName the player's name in the game
 * @param villagerName the villager's own name if it was given one with a name tag, otherwise null
 * @param profession the profession's id without its namespace ({@code farmer}, {@code none}, {@code nitwit}, ...)
 * @param baby whether the villager is a child
 * @param weather the weather where the villager is
 * @param dayTime the time of day in ticks: 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight
 * @param ironGolemNearby whether an iron golem is close to the villager
 * @param emeralds how many emeralds the player has
 */
public record TalkContext(String playerName, String villagerName, String profession, boolean baby, Weather weather, long dayTime,
		boolean ironGolemNearby, int emeralds) {
	public enum Weather {
		CLEAR,
		RAIN,
		THUNDER
	}

	/** The time of day, 0-23999 (the clock counts on from day to day). */
	public int timeOfDay() {
		return (int) Math.floorMod(dayTime, 24_000L);
	}

	/** From sunrise to sunset. */
	public boolean daylight() {
		int time = timeOfDay();
		return time < 12_500 || time >= 23_500;
	}
}
