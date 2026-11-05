package io.github.dylantopic.langmod.core.talk;

import java.util.EnumSet;
import java.util.Set;

/** What a villager understood from one thing the player said. */
final class Heard {
	enum Intent {
		GREET,
		/** "My name is ..." */
		INTRODUCE,
		ASK_NAME,
		NICE_TO_MEET,
		ASK_FEELING,
		/** How the player feels ("I am doing well"). */
		FEELING,
		ASK_JOB,
		ASK_WHERE,
		WEATHER,
		ASK_BREAD,
		/** "Do you have ...?" for anything else. */
		ASK_OTHER,
		/** "Here is one emerald." */
		PAY,
		/** No, after the villager named a price. */
		DECLINE,
		ASK_TIME,
		GOOD_NIGHT,
		ASK_COLOR,
		/** The player names a color they like. */
		COLOR,
		SAFETY,
		THANKS,
		COMPLIMENT,
		/** "Okay", "I see", "That is good to know". */
		AGREE,
		FAREWELL
	}

	enum Greeting {
		HELLO,
		MORNING,
		AFTERNOON,
		EVENING
	}

	enum Feeling {
		GOOD,
		BAD,
		HUNGRY
	}

	enum Place {
		WATER,
		FARM,
		BELL,
		HOUSE,
		BED,
		VILLAGE,
		UNKNOWN
	}

	final Set<Intent> intents = EnumSet.noneOf(Intent.class);
	Greeting greeting = Greeting.HELLO;
	/** The name the player gave, or null. */
	String name;
	Feeling feeling;
	Place place = Place.UNKNOWN;
	/** The color the player named, or null. */
	Color color;
	/** A question mark or a question word. */
	boolean question;
	/** The player said the weather is nice. */
	boolean likesWeather;
	/** "Too", "me too", "저도": agreeing with what the villager said. */
	boolean agreeing;

	void add(Intent intent) {
		intents.add(intent);
	}

	boolean has(Intent intent) {
		return intents.contains(intent);
	}
}
