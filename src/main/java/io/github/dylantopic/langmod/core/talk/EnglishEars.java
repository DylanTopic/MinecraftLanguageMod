package io.github.dylantopic.langmod.core.talk;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.dylantopic.langmod.core.talk.Heard.Feeling;
import io.github.dylantopic.langmod.core.talk.Heard.Greeting;
import io.github.dylantopic.langmod.core.talk.Heard.Intent;
import io.github.dylantopic.langmod.core.talk.Heard.Place;
import io.github.dylantopic.langmod.core.talk.TalkState.Expecting;

/**
 * English: the player's words are lowercased, contractions are spelled out ("what's" -> "what
 * is") and British spellings made American, then whole words and phrases are looked up.
 */
final class EnglishEars implements Ears {
	private static final Pattern NAME = Pattern.compile("\\b(?:my name is|call me|i am called|they call me|people call me) (\\p{L}[\\p{L}\\p{N}_-]*)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Set<String> NOT_NAMES = Set.of("what", "not", "a", "an", "the", "your", "is", "and", "so", "very", "too", "also", "you");
	private static final List<String> QUESTION_STARTS = List.of("what", "where", "how", "who", "when", "why", "which", "is", "are", "do", "does",
			"did", "can", "could", "will", "would", "have", "has", "may");
	private static final Pattern[] CONTRACTIONS = {
			Pattern.compile("(?i)\\b(what|where|how|who|it|that|there|here|name|he|she|when|why|this|everything)'s\\b"),
			Pattern.compile("(?i)\\bi'm\\b"),
			Pattern.compile("(?i)\\b(you|we|they)'re\\b"),
			Pattern.compile("(?i)\\bcan't\\b"),
			Pattern.compile("(?i)\\bwon't\\b"),
			Pattern.compile("(?i)\\b(\\p{L}+)n't\\b"),
			Pattern.compile("(?i)\\b(i|you|we|they|he|she|it)'ll\\b"),
			Pattern.compile("(?i)\\b(i|you|we|they)'ve\\b"),
			Pattern.compile("(?i)\\b(i|you|we|they|he|she)'d\\b"),
			Pattern.compile("(?i)\\blet's\\b")
	};
	private static final String[] EXPANDED = {"$1 is", "i am", "$1 are", "cannot", "will not", "$1 not", "$1 will", "$1 have", "$1 would", "let us"};

	@Override
	public Heard hear(String said, TalkState state) {
		String text = words(said).toLowerCase(Locale.ROOT);
		String t = " " + text + " ";
		Heard heard = new Heard();
		heard.question = said.contains("?") || QUESTION_STARTS.stream().anyMatch(word -> text.equals(word) || text.startsWith(word + " "));
		heard.agreeing = any(t, "too", "me too", "either", "me neither", "same", "as well", "also");

		// Farewells first, so that "good night" and "goodbye" are not taken for greetings.
		if (any(t, "goodbye", "good bye", "bye", "bye bye", "see you", "see ya", "farewell", "take care", "i have to go", "i must go",
				"i need to go", "i gotta go", "i got to go", "i am leaving", "i will go now", "i am going now", "catch you later")) {
			heard.add(Intent.FAREWELL);
		}

		if (any(t, "good night", "goodnight", "sleep well", "sweet dreams", "nighty night")) {
			heard.add(Intent.GOOD_NIGHT);
		}

		if (!heard.has(Intent.FAREWELL)) {
			greeting(text, t, heard);
		}

		Matcher name = NAME.matcher(words(said));

		while (name.find()) {
			if (!NOT_NAMES.contains(name.group(1).toLowerCase(Locale.ROOT))) {
				heard.name = capitalize(name.group(1));
				heard.add(Intent.INTRODUCE);
				break;
			}
		}

		if (any(t, "your name", "who are you", "what are you called", "what do they call you", "what should i call you")) {
			heard.add(Intent.ASK_NAME);
		}

		if (any(t, "nice to meet you", "pleased to meet you", "good to meet you", "glad to meet you", "nice meeting you", "nice to see you",
				"great to meet you")) {
			heard.add(Intent.NICE_TO_MEET);
		}

		if (any(t, "how are you", "how do you feel", "how is it going", "how is your day", "how have you been", "how are things", "are you ok",
				"are you okay", "are you well", "are you happy", "are you fine", "what is up", "how is life", "how you doing")) {
			heard.add(Intent.ASK_FEELING);
		}

		heard.feeling = feeling(t);

		if (heard.feeling != null && (state.expecting == Expecting.FEELING || heard.feeling == Feeling.HUNGRY
				|| any(t, "i am", "i feel", "feeling", "doing"))) {
			heard.add(Intent.FEELING);
		}

		if (any(t, "your job", "what job", "what do you do", "your work", "what work", "do you work", "your profession", "your occupation",
				"what is your role", "what are you doing", "what do you make")) {
			heard.add(Intent.ASK_JOB);
		}

		if (any(t, "where")) {
			heard.add(Intent.ASK_WHERE);
			heard.place = place(t);
		}

		if (any(t, "weather", "sunny", "sunshine", "rain", "raining", "rainy", "storm", "stormy", "thunder", "lightning", "cloudy", "clouds",
				"snow", "snowing", "nice day", "beautiful day", "lovely day", "hot today", "cold today", "sun is out", "clear sky")) {
			heard.add(Intent.WEATHER);
			heard.likesWeather = any(t, "nice", "good", "beautiful", "lovely", "great", "sunny", "wonderful", "perfect", "pleasant");
		}

		pay(t, heard, state);

		if (!heard.has(Intent.PAY) && any(t, "bread", "loaf", "food", "to eat")) {
			heard.add(Intent.ASK_BREAD);
		} else if (!heard.has(Intent.PAY) && any(t, "do you have", "have you got", "can i have", "can i buy", "do you sell", "may i have",
				"i want to buy", "i would like")) {
			heard.add(Intent.ASK_OTHER);
		}

		if (any(t, "what time", "the time", "time is it", "is it late", "is it night", "is it day", "is it morning", "is it evening", "how late",
				"what hour", "is it dark", "o clock", "it is late", "getting late", "getting dark")) {
			heard.add(Intent.ASK_TIME);
		}

		heard.color = Color.inEnglish(t);

		if (any(t, "favorite color", "what color", "which color", "colors do you like", "color do you like", "your color")
				&& (heard.color == null || any(t, "what", "which", "yours", "you"))) {
			heard.add(Intent.ASK_COLOR);
		}

		if (heard.color != null && (state.expecting == Expecting.COLOR || any(t, "my favorite", "i like", "i love", "my color", "i prefer", "mine is"))) {
			heard.add(Intent.COLOR);
		}

		if (any(t, "safe", "safety", "dangerous", "danger", "zombie", "zombies", "monster", "monsters", "mobs", "pillager", "pillagers", "raid",
				"raids", "protect", "protects", "protected", "iron golem", "golem", "attack", "attacks")) {
			heard.add(Intent.SAFETY);
		}

		if (any(t, "thank you", "thanks", "thank", "thx", "cheers")) {
			heard.add(Intent.THANKS);
		}

		if (any(t, "cool", "awesome", "amazing", "great", "nice", "wow", "wonderful", "interesting", "impressive", "neat", "beautiful", "lovely",
				"fantastic", "fun")) {
			heard.add(Intent.COMPLIMENT);
		}

		if (heard.agreeing || any(t, "okay", "ok", "yes", "yeah", "yep", "sure", "good to know", "i see", "alright", "all right", "got it",
				"that is good", "right", "of course", "understood", "no", "nope", "really", "hmm", "agreed", "i agree", "good", "glad")) {
			heard.add(Intent.AGREE);
		}

		return heard;
	}

	private static void greeting(String text, String t, Heard heard) {
		if (any(t, "good morning") || text.startsWith("morning")) {
			heard.greeting = Greeting.MORNING;
		} else if (any(t, "good afternoon") || text.startsWith("afternoon")) {
			heard.greeting = Greeting.AFTERNOON;
		} else if (any(t, "good evening") || text.startsWith("evening")) {
			heard.greeting = Greeting.EVENING;
		} else if (!any(t, "hello", "hi", "hey", "hiya", "howdy", "greetings", "hullo")) {
			return;
		}

		heard.add(Intent.GREET);
	}

	private static Feeling feeling(String t) {
		if (any(t, "hungry", "starving")) {
			return Feeling.HUNGRY;
		}

		if (any(t, "not bad", "not too bad")) {
			return Feeling.GOOD;
		}

		if (any(t, "not good", "not well", "not great", "not fine", "not okay", "not ok", "not happy", "sad", "tired", "bad", "sick", "terrible",
				"awful", "angry", "scared", "lonely", "bored", "upset", "unhappy", "sleepy", "worried")) {
			return Feeling.BAD;
		}

		if (any(t, "well", "good", "fine", "great", "happy", "okay", "ok", "wonderful", "excellent", "awesome", "alright", "all right", "amazing",
				"fantastic", "super")) {
			return Feeling.GOOD;
		}

		return null;
	}

	private static Place place(String t) {
		if (any(t, "water", "river", "lake", "sea", "ocean", "pond")) {
			return Place.WATER;
		}

		if (any(t, "farm", "farms", "field", "fields", "crops", "wheat", "garden")) {
			return Place.FARM;
		}

		if (any(t, "bell", "meeting point", "center", "centre", "middle")) {
			return Place.BELL;
		}

		if (any(t, "bed", "beds")) {
			return Place.BED;
		}

		if (any(t, "house", "home", "houses", "hut")) {
			return Place.HOUSE;
		}

		if (any(t, "village", "town")) {
			return Place.VILLAGE;
		}

		return any(t, "the well", "a well") ? Place.WATER : Place.UNKNOWN;
	}

	private static void pay(String t, Heard heard, TalkState state) {
		boolean emerald = any(t, "emerald", "emeralds");
		boolean giving = any(t, "here is", "here are", "here you go", "here you are", "here it is", "take it", "take this", "i will pay",
				"i will take", "i will buy", "i pay", "for you", "give you", "deal");
		boolean yes = any(t, "yes", "yeah", "yep", "sure", "okay", "ok", "please", "alright", "all right", "buy");

		if ((emerald && (giving || !heard.question)) || (state.expecting == Expecting.PAYMENT && (giving || yes))) {
			heard.add(Intent.PAY);
		} else if (state.expecting == Expecting.PAYMENT && any(t, "no", "nope", "no thanks", "no thank you", "not now", "maybe later", "never mind",
				"do not want", "too expensive", "later")) {
			heard.add(Intent.DECLINE);
		}
	}

	/** The words of what was said, without punctuation; contractions spelled out and British spellings made American. */
	static String words(String said) {
		String text = Normalizer.normalize(said, Normalizer.Form.NFKC).replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'');

		for (int i = 0; i < CONTRACTIONS.length; i++) {
			text = CONTRACTIONS[i].matcher(text).replaceAll(EXPANDED[i]);
		}

		text = text.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
		return (" " + text + " ").replaceAll("(?i) favourite ", " favorite ").replaceAll("(?i) colour(s?) ", " color$1 ")
				.replaceAll("(?i) grey ", " gray ").trim();
	}

	/** Whether any of the words or phrases is in the padded text. */
	private static boolean any(String padded, String... phrases) {
		for (String phrase : phrases) {
			if (padded.contains(" " + phrase + " ")) {
				return true;
			}
		}

		return false;
	}

	private static String capitalize(String name) {
		return name.equals(name.toLowerCase(Locale.ROOT)) ? name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) : name;
	}
}
