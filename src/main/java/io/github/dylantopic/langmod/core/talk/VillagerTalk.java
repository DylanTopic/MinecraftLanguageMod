package io.github.dylantopic.langmod.core.talk;

import static io.github.dylantopic.langmod.core.talk.Line.of;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.talk.Heard.Intent;
import io.github.dylantopic.langmod.core.talk.TalkState.Expecting;

/**
 * Conversations with villagers. A villager understands what the player says by its key words
 * (greetings, names, its job, directions, the weather, bread, the time, colors, safety, goodbyes),
 * so the player does not have to say it perfectly, and answers from what is really there: its own
 * profession and name, the weather, the time of day, an iron golem nearby. It remembers what it
 * asked ("How are you?", "What is yours?") and the price it named, for the answer that follows.
 */
public final class VillagerTalk {
	private static final Set<Language> LANGUAGES = EnumSet.of(Language.KOREAN, Language.ENGLISH);
	/** What is only agreeing with the villager when said with "too" ("I like the sun too"). */
	private static final Set<Intent> STATEMENTS = EnumSet.of(Intent.WEATHER, Intent.COMPLIMENT, Intent.AGREE, Intent.NICE_TO_MEET, Intent.FEELING,
			Intent.COLOR, Intent.THANKS);
	/** Speech recognition's inventions when it hears noise (video subtitles it learned from). */
	private static final List<String> ENGLISH_NOISE = List.of("thank you for watching", "thanks for watching", "subscribe", "amara.org", "subtitles by");
	private static final List<String> KOREAN_NOISE = List.of("시청해주셔서", "시청해 주셔서", "구독", "자막", "다음 영상", "mbc", "kbs", "sbs");

	private VillagerTalk() {
	}

	/** The languages villagers can talk in. */
	public static boolean supports(Language language) {
		return LANGUAGES.contains(language);
	}

	/** The villager's answer to what the player said. Updates what the villager remembers. */
	public static TalkReply reply(Language language, String said, TalkContext context, TalkState state) {
		if (!supports(language)) {
			throw new IllegalArgumentException("Villagers do not speak " + language.englishName());
		}

		Heard heard = ears(language).hear(said, state);
		Expecting expected = state.expecting;
		state.expecting = Expecting.NOTHING;

		if (heard.name != null) {
			state.playerName = heard.name.equalsIgnoreCase(context.playerName()) ? context.playerName() : heard.name;
		} else if (heard.has(Intent.INTRODUCE) && state.playerName == null) {
			state.playerName = context.playerName();
		}

		List<Line> lines = new ArrayList<>();
		TalkReply.Mood mood = TalkReply.Mood.TALK;
		TalkReply.Action action = TalkReply.Action.NONE;

		// "I like the sun too", "저도요": the villager agrees.
		boolean agreeingOnly = heard.agreeing && !heard.question && STATEMENTS.containsAll(heard.intents)
				&& !(expected == Expecting.FEELING && heard.has(Intent.FEELING)) && !(expected == Expecting.COLOR && heard.has(Intent.COLOR))
				&& !(heard.has(Intent.NICE_TO_MEET) && !state.met);

		if (!agreeingOnly) {
			if (heard.has(Intent.GREET)) {
				lines.add(Replies.greet(heard.greeting, state.playerName));
			} else if (heard.has(Intent.INTRODUCE) && state.met) {
				lines.add(Replies.greet(Heard.Greeting.HELLO, state.playerName));
			}

			if (heard.has(Intent.ASK_NAME)) {
				lines.add(Replies.name(context.villagerName()));
			}

			if (!state.met && (heard.has(Intent.INTRODUCE) || heard.has(Intent.ASK_NAME) || heard.has(Intent.NICE_TO_MEET))) {
				lines.add(heard.has(Intent.NICE_TO_MEET) ? Replies.NICE_TO_MEET_YOU_TOO : Replies.NICE_TO_MEET_YOU);
				state.met = true;
			}

			if (heard.has(Intent.FEELING)) {
				switch (heard.feeling) {
					case GOOD -> lines.add(Replies.GLAD);
					case BAD -> lines.add(Replies.GET_WELL);
					case HUNGRY -> {
						lines.add(Replies.HUNGRY);
						state.expecting = Expecting.PAYMENT;
					}
				}
			}

			if (heard.has(Intent.ASK_FEELING)) {
				boolean askBack = !heard.has(Intent.FEELING);
				lines.add(Replies.howAreYou(context.weather(), askBack));

				if (askBack) {
					state.expecting = Expecting.FEELING;
				}
			}

			if (heard.has(Intent.ASK_JOB)) {
				lines.add(Replies.job(context.profession(), context.baby()));
			}

			if (heard.has(Intent.ASK_WHERE)) {
				lines.add(Replies.where(heard.place));
			}

			if (heard.has(Intent.WEATHER)) {
				lines.add(Replies.weather(context.weather(), context.daylight(), heard.likesWeather));
			}

			if (heard.has(Intent.ASK_BREAD)) {
				lines.add(Replies.BREAD);
				state.expecting = Expecting.PAYMENT;
			}

			if (heard.has(Intent.PAY)) {
				if (expected != Expecting.PAYMENT) {
					lines.add(Replies.KEEP_IT);
				} else if (context.emeralds() > 0) {
					lines.add(Replies.SOLD);
					action = TalkReply.Action.SELL_BREAD;
					mood = TalkReply.Mood.YES;
				} else {
					lines.add(Replies.NO_EMERALD);
					state.expecting = Expecting.PAYMENT;
					mood = TalkReply.Mood.NO;
				}
			}

			if (heard.has(Intent.DECLINE)) {
				lines.add(Replies.NEXT_TIME);
			}

			if (heard.has(Intent.ASK_TIME)) {
				lines.add(Replies.time(context.timeOfDay()));
			}

			if (heard.has(Intent.COLOR)) {
				lines.add(Replies.niceColor(heard.color));
			}

			if (heard.has(Intent.ASK_COLOR)) {
				boolean askBack = !heard.has(Intent.COLOR);
				lines.add(Replies.favoriteColor(askBack));

				if (askBack) {
					state.expecting = Expecting.COLOR;
				}
			}

			if (heard.has(Intent.SAFETY)) {
				lines.add(Replies.safety(context.ironGolemNearby()));
			}

			if (heard.has(Intent.GOOD_NIGHT)) {
				lines.add(Replies.GOOD_NIGHT);
			} else if (heard.has(Intent.FAREWELL)) {
				lines.add(state.saidGoodbye ? Replies.SEE_YOU : Replies.GOODBYE);
				state.saidGoodbye = true;
			}
		}

		if (lines.isEmpty()) {
			if (agreeingOnly) {
				lines.add(Replies.NOD);
				mood = TalkReply.Mood.YES;
			} else if (heard.has(Intent.THANKS)) {
				lines.add(Replies.WELCOME);
			} else if (heard.has(Intent.NICE_TO_MEET)) {
				lines.add(Replies.NOD); // they have met already
				mood = TalkReply.Mood.YES;
			} else if (heard.has(Intent.ASK_OTHER)) {
				lines.add(Replies.DONT_HAVE);
				mood = TalkReply.Mood.NO;
			} else if (heard.has(Intent.COMPLIMENT)) {
				lines.add(Replies.THANK_YOU);
			} else if (!heard.intents.isEmpty()) {
				lines.add(Replies.NOD);
				mood = TalkReply.Mood.YES;
			} else {
				lines.add(Replies.CONFUSED);
				mood = TalkReply.Mood.NO;
				state.expecting = expected; // still waiting for the answer to its question
			}
		}

		state.misunderstood = lines.getFirst() == Replies.CONFUSED ? state.misunderstood + 1 : 0;
		Line reply = Line.join(lines);
		return new TalkReply(reply.in(language), language == Language.ENGLISH ? null : reply.english(),
				reply.next() == null ? null : reply.next().phrase(language), mood, action);
	}

	/** Things to say to a villager, one for each kind of thing villagers understand. */
	public static List<Phrase> examples(Language language, String playerName) {
		List<Line> lines = List.of(
				of("Hello. My name is " + playerName + ". What is your name?", "안녕하세요. 제 이름은 " + playerName + Korean.copula(playerName) + ". 이름이 뭐예요?"),
				of("Good morning. How are you today?", "좋은 아침이에요. 오늘 기분이 어때요?"),
				of("What is your job?", "직업이 뭐예요?"),
				of("Excuse me, where is the water?", "실례합니다, 물이 어디에 있어요?"),
				of("The weather is nice today.", "오늘 날씨가 좋네요."),
				of("Do you have any bread?", "빵 있어요?"),
				of("What time is it?", "지금 몇 시예요?"),
				of("What is your favorite color?", "제일 좋아하는 색이 뭐예요?"),
				of("Is this village safe?", "이 마을은 안전해요?"),
				of("I have to go explore now. Goodbye.", "이제 탐험하러 가야 해요. 안녕히 계세요."));
		return lines.stream().map(line -> line.phrase(language)).toList();
	}

	/** Whether speech recognition made up what it heard from noise, or heard nothing. */
	public static boolean isNoise(Language language, String said) {
		String text = said.toLowerCase(Locale.ROOT).strip();

		if (text.isEmpty() || text.chars().noneMatch(Character::isLetterOrDigit)) {
			return true;
		}

		return (language == Language.KOREAN ? KOREAN_NOISE : ENGLISH_NOISE).stream().anyMatch(text::contains);
	}

	private static Ears ears(Language language) {
		return language == Language.KOREAN ? new KoreanEars() : new EnglishEars();
	}
}
