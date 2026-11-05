package io.github.dylantopic.langmod.core.talk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.talk.TalkContext.Weather;

class VillagerTalkTest {
	/** A farmer at noon on a clear day, an iron golem nearby, talking to a player with 3 emeralds. */
	private static final TalkContext FARMER = new TalkContext("Steve", null, "farmer", false, Weather.CLEAR, 6_000, true, 3);
	/** The same farmer at sunset (the clock counts on from day to day). */
	private static final TalkContext EVENING = new TalkContext("Steve", null, "farmer", false, Weather.CLEAR, 24_000 * 3 + 12_000, true, 3);

	/** Says each line in turn to one villager and returns its answers. */
	private static List<TalkReply> talk(Language language, TalkContext context, String... lines) {
		TalkState state = new TalkState();
		return List.of(lines).stream().map(line -> VillagerTalk.reply(language, line, context, state)).toList();
	}

	private static String say(Language language, TalkContext context, String line) {
		return talk(language, context, line).getFirst().text();
	}

	// --- The ten conversations, in English -----------------------------------------------------

	@Test
	void englishGreetingAndNames() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "Hello. My name is Dylan. What is your name?", "Nice to meet you too.");
		assertEquals("Hello, Dylan. I am a villager. Nice to meet you.", replies.get(0).text());
		assertNull(replies.get(0).translation(), "no translation of English");
		assertEquals("Nice to meet you too.", replies.get(0).suggestion().text());
		assertEquals("Hmm!", replies.get(1).text());
		assertEquals(TalkReply.Mood.YES, replies.get(1).mood());
	}

	@Test
	void englishFeelings() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "Good morning. How are you today?", "I am doing well.");
		assertEquals("Good morning! I am happy, thank you. How are you?", replies.get(0).text());
		assertEquals("That is good to hear!", replies.get(1).text());
		assertEquals("Oh no! I hope you feel better soon.", talk(Language.ENGLISH, FARMER, "How are you?", "I'm a bit tired.").get(1).text());
	}

	@Test
	void englishJob() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "What is your job?", "That is very cool.");
		assertEquals("I am a farmer. I grow wheat.", replies.get(0).text());
		assertEquals("Thank you!", replies.get(1).text());
	}

	@Test
	void englishDirections() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "Excuse me, where is the water?", "Thank you very much!");
		assertEquals("The water is next to the farm.", replies.get(0).text());
		assertEquals("You are welcome!", replies.get(1).text());
		assertEquals("The bell is in the middle of the village.", say(Language.ENGLISH, FARMER, "where's the bell"));
		assertEquals("Sorry, I do not know.", say(Language.ENGLISH, FARMER, "Where is the dragon?"));
	}

	@Test
	void englishWeather() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "The weather is nice today.", "I like the sun too.");
		assertEquals("Yes, it is sunny. I like the sun.", replies.get(0).text());
		assertEquals("Hmm!", replies.get(1).text(), "agreeing gets a nod, not the weather again");
	}

	@Test
	void englishBread() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "Do you have any bread?", "Here is one emerald.");
		assertEquals("Yes, I have bread. It costs one emerald.", replies.get(0).text());
		assertEquals("Thank you! Here is your bread.", replies.get(1).text());
		assertEquals(TalkReply.Action.SELL_BREAD, replies.get(1).action());
		assertEquals(TalkReply.Action.NONE, replies.get(0).action());
	}

	@Test
	void englishTime() {
		List<TalkReply> replies = talk(Language.ENGLISH, EVENING, "What time is it?", "Okay, good night.");
		assertEquals("It is getting dark. You should sleep soon.", replies.get(0).text());
		assertEquals("Good night! Sleep well.", replies.get(1).text());
	}

	@Test
	void englishColors() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "What is your favorite color?", "My favorite color is blue.");
		assertEquals("My favorite color is green, like an emerald. What is yours?", replies.get(0).text());
		assertEquals("Blue is a nice color!", replies.get(1).text());
		assertEquals("Light blue is a nice color!", talk(Language.ENGLISH, FARMER, "What's your favourite colour?", "Light blue.").get(1).text());
	}

	@Test
	void englishSafety() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "Is this village safe?", "That is good to know.");
		assertEquals("Yes, the Iron Golem protects us from zombies.", replies.get(0).text());
		assertEquals("Hmm!", replies.get(1).text());
	}

	@Test
	void englishFarewell() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "I have to go explore now. Goodbye.", "See you later.");
		assertEquals("Goodbye! Have a safe trip.", replies.get(0).text());
		assertEquals("See you later!", replies.get(1).text());
	}

	// --- The same conversations in Korean -------------------------------------------------------

	@Test
	void koreanGreetingAndNames() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "안녕하세요. 제 이름은 딜런이에요. 이름이 뭐예요?", "저도 만나서 반가워요.");
		assertEquals("안녕하세요, 딜런님. 저는 마을 주민이에요. 만나서 반가워요.", replies.get(0).text());
		assertEquals("Hello, 딜런. I am a villager. Nice to meet you.", replies.get(0).translation());
		assertEquals("저도 만나서 반가워요.", replies.get(0).suggestion().text());
		assertEquals("Nice to meet you too.", replies.get(0).suggestion().translation());
		assertEquals("음!", replies.get(1).text());
		// A name in Latin letters, and the name given by speech recognition without spaces.
		assertEquals("안녕하세요, Dylan님. 저는 마을 주민이에요. 만나서 반가워요.", say(Language.KOREAN, FARMER, "안녕하세요. 제 이름은 Dylan입니다. 이름이 뭐예요?"));
		assertEquals("안녕하세요, 민수님. 저는 마을 주민이에요. 만나서 반가워요.", say(Language.KOREAN, FARMER, "안녕하세요 제이름은민수예요 이름이뭐예요"));
	}

	@Test
	void koreanFeelings() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "좋은 아침이에요. 오늘 기분이 어때요?", "저는 잘 지내요.");
		assertEquals("좋은 아침이에요! 저는 행복해요, 고마워요. 잘 지내요?", replies.get(0).text());
		assertEquals("Good morning! I am happy, thank you. How are you?", replies.get(0).translation());
		assertEquals("다행이에요!", replies.get(1).text());
		assertEquals("아이고! 빨리 나아지면 좋겠어요.", talk(Language.KOREAN, FARMER, "잘 지내요?", "좀 피곤해요.").get(1).text());
	}

	@Test
	void koreanJob() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "직업이 뭐예요?", "정말 멋있어요.");
		assertEquals("저는 농부예요. 밀을 키워요.", replies.get(0).text());
		assertEquals("고마워요!", replies.get(1).text());
	}

	@Test
	void koreanDirections() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "실례합니다, 물이 어디에 있어요?", "정말 감사합니다!");
		assertEquals("물은 농장 옆에 있어요.", replies.get(0).text());
		assertEquals("천만에요!", replies.get(1).text());
		assertEquals("물은 농장 옆에 있어요.", say(Language.KOREAN, FARMER, "어디에 물이 있어요?"));
		assertEquals("종은 마을 한가운데에 있어요.", say(Language.KOREAN, FARMER, "종은 어디 있어요?"));
		assertEquals("죄송해요, 잘 모르겠어요.", say(Language.KOREAN, FARMER, "동물이 어디에 있어요?"), "동물 is not water");
	}

	@Test
	void koreanWeather() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "오늘 날씨가 좋네요.", "저도 해를 좋아해요.");
		assertEquals("네, 날씨가 맑아요. 저는 해를 좋아해요.", replies.get(0).text());
		assertEquals("음!", replies.get(1).text());
	}

	@Test
	void koreanBread() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "빵 있어요?", "여기 에메랄드 한 개 있어요.");
		assertEquals("네, 빵 있어요. 에메랄드 한 개예요.", replies.get(0).text());
		assertEquals("고마워요! 여기 빵이에요.", replies.get(1).text());
		assertEquals(TalkReply.Action.SELL_BREAD, replies.get(1).action());
	}

	@Test
	void koreanTime() {
		List<TalkReply> replies = talk(Language.KOREAN, EVENING, "지금 몇 시예요?", "알겠어요, 잘 자요.");
		assertEquals("어두워지고 있어요. 곧 자야 해요.", replies.get(0).text());
		assertEquals("잘 자요! 좋은 꿈 꿔요.", replies.get(1).text());
	}

	@Test
	void koreanColors() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "제일 좋아하는 색이 뭐예요?", "제가 제일 좋아하는 색은 파란색이에요.");
		assertEquals("제가 제일 좋아하는 색은 초록색이에요. 에메랄드처럼요! 어떤 색을 좋아해요?", replies.get(0).text());
		assertEquals("파란색은 예쁜 색이에요!", replies.get(1).text());
		assertEquals("보라색은 예쁜 색이에요!", talk(Language.KOREAN, FARMER, "무슨 색 좋아해요?", "보라색이요").get(1).text());
		assertEquals("초록색이요? 저도요!", talk(Language.KOREAN, FARMER, "어떤 색을 좋아해요?", "초록").get(1).text());
	}

	@Test
	void koreanSafety() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "이 마을은 안전해요?", "다행이네요.");
		assertEquals("네, 철 골렘이 좀비로부터 우리를 지켜 줘요.", replies.get(0).text());
		assertEquals("음!", replies.get(1).text());
	}

	@Test
	void koreanFarewell() {
		List<TalkReply> replies = talk(Language.KOREAN, FARMER, "이제 탐험하러 가야 해요. 안녕히 계세요.", "다음에 봐요.");
		assertEquals("안녕히 가세요! 조심히 다녀오세요.", replies.get(0).text());
		assertEquals("또 봐요!", replies.get(1).text());
	}

	// --- The world around the villager ----------------------------------------------------------

	@Test
	void answersFollowTheWorld() {
		TalkContext rain = new TalkContext("Steve", null, "farmer", false, Weather.RAIN, 6_000, false, 0);
		assertEquals("Really? It is raining. I do not like the rain.", say(Language.ENGLISH, rain, "The weather is nice today."));
		assertEquals("오늘은 비가 와요. 저는 비를 안 좋아해요.", say(Language.KOREAN, rain, "날씨가 어때요?"));
		assertEquals("I am a little sad. It is raining. How are you?", say(Language.ENGLISH, rain, "How are you?"));
		assertEquals("I hope so. There is no Iron Golem here.", say(Language.ENGLISH, rain, "Is it safe here?"));

		TalkContext night = new TalkContext("Steve", null, "farmer", false, Weather.CLEAR, 18_000, true, 3);
		assertEquals("It is night. Zombies come out at night! You should sleep.", say(Language.ENGLISH, night, "What time is it?"));
		assertEquals("It is night, but the sky is clear. I like the stars.", say(Language.ENGLISH, night, "Nice weather!"));
		assertEquals("It is the afternoon. The sun is high.", say(Language.ENGLISH, FARMER, "what time is it"));
		assertEquals("It is getting dark. You should sleep soon.", say(Language.ENGLISH, EVENING, "It's getting late."));
	}

	@Test
	void eachVillagerHasItsOwnJob() {
		assertEquals("I am a librarian. I like books.", say(Language.ENGLISH, job("librarian", false), "What do you do?"));
		assertEquals("I am an armorer. I make armor.", say(Language.ENGLISH, job("armorer", false), "What's your job?"));
		assertEquals("저는 석공이에요. 돌을 다듬어요.", say(Language.KOREAN, job("mason", false), "무슨 일 하세요?"));
		assertEquals("저는 아직 직업이 없어요. 일을 찾고 있어요.", say(Language.KOREAN, job("none", false), "직업이 뭐예요?"));
		assertEquals("저는 사서예요. 책을 좋아해요.", say(Language.KOREAN, job("librarian", false), "뭐 하는 사람이에요?"));
		assertEquals("I am a child. I like to play!", say(Language.ENGLISH, job("none", true), "What is your job?"));
		assertEquals("I am a villager. I work in the village.", say(Language.ENGLISH, job("alchemist", false), "What is your job?"));
	}

	@Test
	void villagerWithANameTag() {
		TalkContext bob = new TalkContext("Steve", "Bob", "farmer", false, Weather.CLEAR, 6_000, true, 3);
		assertEquals("Hello! My name is Bob. Nice to meet you.", say(Language.ENGLISH, bob, "Hi! What's your name?"));
		assertEquals("제 이름은 Bob입니다. 만나서 반가워요.", say(Language.KOREAN, bob, "이름이 뭐예요?"));
		TalkContext minsu = new TalkContext("Steve", "민수", "farmer", false, Weather.CLEAR, 6_000, true, 3);
		assertEquals("제 이름은 민수예요. 만나서 반가워요.", say(Language.KOREAN, minsu, "성함이 어떻게 되세요?"));
	}

	@Test
	void breadNeedsAnEmerald() {
		TalkContext poor = new TalkContext("Steve", null, "farmer", false, Weather.CLEAR, 6_000, true, 0);
		List<TalkReply> replies = talk(Language.ENGLISH, poor, "Can I have some bread?", "Yes please.", "No thanks.");
		assertEquals("Hmm, you do not have an emerald.", replies.get(1).text());
		assertEquals(TalkReply.Action.NONE, replies.get(1).action());
		assertEquals("Okay. Maybe next time.", replies.get(2).text(), "still waiting to be paid after no emerald");
		assertEquals("Thank you, but you can keep it.", say(Language.ENGLISH, FARMER, "Here is an emerald."), "nothing was for sale");
		assertEquals("알겠어요. 다음에 또 와요.", talk(Language.KOREAN, FARMER, "빵 있어요?", "아니요, 괜찮아요.").get(1).text());
	}

	@Test
	void theNameIsRemembered() {
		List<TalkReply> replies = talk(Language.ENGLISH, FARMER, "My name is steve.", "Good evening!");
		assertEquals("Nice to meet you.", replies.get(0).text());
		assertEquals("Good evening, Steve!", replies.get(1).text(), "the player's name as the game spells it");
		assertEquals("Hello!", say(Language.ENGLISH, FARMER, "Hello"), "no name before the player gives it");
	}

	@Test
	void notUnderstood() {
		TalkState state = new TalkState();
		TalkReply reply = VillagerTalk.reply(Language.ENGLISH, "Purple monkey dishwasher.", FARMER, state);
		assertEquals("Hmm? Sorry, I do not understand.", reply.text());
		assertEquals(TalkReply.Mood.NO, reply.mood());
		assertEquals(1, state.misunderstood());
		assertEquals("음? 죄송해요, 잘 못 알아들었어요.", say(Language.KOREAN, FARMER, "Hello, how are you?"), "Korean villagers want Korean");
		// What it asked is still waiting for an answer after a line it did not understand.
		assertEquals("Blue is a nice color!", talk(Language.ENGLISH, FARMER, "What is your favorite color?", "Hmmmm", "Blue").get(2).text());
	}

	@Test
	void everyExampleIsUnderstood() {
		for (Language language : List.of(Language.ENGLISH, Language.KOREAN)) {
			List<Phrase> examples = VillagerTalk.examples(language, "Dylan");
			assertEquals(10, examples.size());

			for (Phrase example : examples) {
				TalkReply reply = talk(language, FARMER, example.text()).getFirst();
				assertNotEquals(TalkReply.Mood.NO, reply.mood(), example.text() + " -> " + reply.text());
				assertTrue(reply.suggestion() != null, "a suggested answer after " + example.text());
				assertEquals(language == Language.ENGLISH, example.translation() == null);
			}
		}

		assertEquals("안녕하세요. 제 이름은 Dylan입니다. 이름이 뭐예요?", VillagerTalk.examples(Language.KOREAN, "Dylan").getFirst().text());
	}

	@Test
	void noiseIsNotAnAnswer() {
		assertTrue(VillagerTalk.isNoise(Language.ENGLISH, " Thank you for watching!"));
		assertTrue(VillagerTalk.isNoise(Language.ENGLISH, "..."));
		assertTrue(VillagerTalk.isNoise(Language.KOREAN, "시청해주셔서 감사합니다."));
		assertTrue(VillagerTalk.isNoise(Language.KOREAN, "MBC 뉴스 이덕영입니다."));
		assertFalse(VillagerTalk.isNoise(Language.KOREAN, "빵 있어요?"));
		assertFalse(VillagerTalk.isNoise(Language.ENGLISH, "Thank you very much!"));
	}

	@Test
	void languages() {
		assertTrue(VillagerTalk.supports(Language.KOREAN));
		assertTrue(VillagerTalk.supports(Language.ENGLISH));
		assertFalse(VillagerTalk.supports(Language.SPANISH));
	}

	@Test
	void koreanEndings() {
		assertEquals("예요", Korean.copula("농부"));
		assertEquals("이에요", Korean.copula("석공"));
		assertEquals("입니다", Korean.copula("Bob"));
		assertEquals("은", Korean.topic("파란색"));
		assertEquals("는", Korean.topic("보라"));
	}

	private static TalkContext job(String profession, boolean baby) {
		return new TalkContext("Steve", null, profession, baby, Weather.CLEAR, 6_000, true, 3);
	}
}
