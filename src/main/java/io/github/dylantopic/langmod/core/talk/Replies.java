package io.github.dylantopic.langmod.core.talk;

import static io.github.dylantopic.langmod.core.talk.Line.of;

import io.github.dylantopic.langmod.core.talk.Heard.Greeting;
import io.github.dylantopic.langmod.core.talk.Heard.Place;
import io.github.dylantopic.langmod.core.talk.TalkContext.Weather;

/** What villagers say, in English and Korean (polite 해요체), with what the player could say back. */
final class Replies {
	static final Line I_AM_A_VILLAGER = of("I am a villager.", "저는 마을 주민이에요.");
	static final Line NICE_TO_MEET_YOU = of("Nice to meet you.", "만나서 반가워요.").then("Nice to meet you too.", "저도 만나서 반가워요.");
	static final Line NICE_TO_MEET_YOU_TOO = of("Nice to meet you too!", "저도 만나서 반가워요!");
	static final Line GLAD = of("That is good to hear!", "다행이에요!");
	static final Line GET_WELL = of("Oh no! I hope you feel better soon.", "아이고! 빨리 나아지면 좋겠어요.");
	static final Line PAY = of("Here is one emerald.", "여기 에메랄드 한 개 있어요.");
	static final Line HUNGRY = of("Oh no! I have bread. It costs one emerald.", "아이고! 저한테 빵이 있어요. 에메랄드 한 개예요.")
			.then(PAY.english(), PAY.korean());
	static final Line BREAD = of("Yes, I have bread. It costs one emerald.", "네, 빵 있어요. 에메랄드 한 개예요.").then(PAY.english(), PAY.korean());
	static final Line DONT_HAVE = of("Sorry, I do not have that.", "죄송해요, 그건 없어요.");
	static final Line SOLD = of("Thank you! Here is your bread.", "고마워요! 여기 빵이에요.").then("Thank you!", "고마워요!");
	static final Line NO_EMERALD = of("Hmm, you do not have an emerald.", "음, 에메랄드가 없네요.");
	static final Line KEEP_IT = of("Thank you, but you can keep it.", "고마워요, 하지만 괜찮아요.");
	static final Line NEXT_TIME = of("Okay. Maybe next time.", "알겠어요. 다음에 또 와요.");
	static final Line GOOD_NIGHT = of("Good night! Sleep well.", "잘 자요! 좋은 꿈 꿔요.");
	static final Line WELCOME = of("You are welcome!", "천만에요!");
	static final Line THANK_YOU = of("Thank you!", "고마워요!");
	/** A villager's "hmm" with a nod. */
	static final Line NOD = of("Hmm!", "음!");
	static final Line GOODBYE = of("Goodbye! Have a safe trip.", "안녕히 가세요! 조심히 다녀오세요.").then("See you later.", "다음에 봐요.");
	static final Line SEE_YOU = of("See you later!", "또 봐요!");
	static final Line CONFUSED = of("Hmm? Sorry, I do not understand.", "음? 죄송해요, 잘 못 알아들었어요.");

	private Replies() {
	}

	static Line greet(Greeting greeting, String name) {
		if (name == null) {
			return switch (greeting) {
				case HELLO -> of("Hello!", "안녕하세요!");
				case MORNING -> of("Good morning!", "좋은 아침이에요!");
				case AFTERNOON -> of("Good afternoon!", "좋은 오후예요!");
				case EVENING -> of("Good evening!", "좋은 저녁이에요!");
			};
		}

		return switch (greeting) {
			case HELLO -> of("Hello, " + name + ".", "안녕하세요, " + name + "님.");
			case MORNING -> of("Good morning, " + name + "!", "좋은 아침이에요, " + name + "님!");
			case AFTERNOON -> of("Good afternoon, " + name + "!", "좋은 오후예요, " + name + "님!");
			case EVENING -> of("Good evening, " + name + "!", "좋은 저녁이에요, " + name + "님!");
		};
	}

	/** Answer to "What is your name?": its name tag's name if it has one. */
	static Line name(String villagerName) {
		return villagerName == null ? I_AM_A_VILLAGER
				: of("My name is " + villagerName + ".", "제 이름은 " + villagerName + Korean.copula(villagerName) + ".");
	}

	/** "How are you?" The villager's mood follows the weather; it asks back unless the player already said. */
	static Line howAreYou(Weather weather, boolean askBack) {
		Line line = switch (weather) {
			case CLEAR -> askBack ? of("I am happy, thank you. How are you?", "저는 행복해요, 고마워요. 잘 지내요?")
					: of("I am happy, thank you.", "저는 행복해요, 고마워요.");
			case RAIN -> askBack ? of("I am a little sad. It is raining. How are you?", "저는 조금 슬퍼요. 비가 와요. 잘 지내요?")
					: of("I am a little sad. It is raining.", "저는 조금 슬퍼요. 비가 와요.");
			case THUNDER -> askBack ? of("I am scared of the storm! How are you?", "폭풍이 무서워요! 잘 지내요?") : of("I am scared of the storm!", "폭풍이 무서워요!");
		};
		return askBack ? line.then("I am doing well.", "저는 잘 지내요.") : line;
	}

	static Line job(String profession, boolean baby) {
		if (baby) {
			return of("I am a child. I like to play!", "저는 아이예요. 노는 것을 좋아해요!").then("That sounds fun!", "재미있겠네요!");
		}

		Line job = switch (profession) {
			case "farmer" -> of("I am a farmer. I grow wheat.", "저는 농부예요. 밀을 키워요.");
			case "armorer" -> of("I am an armorer. I make armor.", "저는 갑옷 제조인이에요. 갑옷을 만들어요.");
			case "butcher" -> of("I am a butcher. I sell meat.", "저는 도살업자예요. 고기를 팔아요.");
			case "cartographer" -> of("I am a cartographer. I make maps.", "저는 지도 제작자예요. 지도를 만들어요.");
			case "cleric" -> of("I am a cleric. I help people.", "저는 성직자예요. 사람들을 도와요.");
			case "fisherman" -> of("I am a fisherman. I catch fish.", "저는 어부예요. 물고기를 잡아요.");
			case "fletcher" -> of("I am a fletcher. I make bows and arrows.", "저는 화살 제조인이에요. 활과 화살을 만들어요.");
			case "leatherworker" -> of("I am a leatherworker. I make clothes from leather.", "저는 가죽 세공인이에요. 가죽으로 옷을 만들어요.");
			case "librarian" -> of("I am a librarian. I like books.", "저는 사서예요. 책을 좋아해요.");
			case "mason" -> of("I am a mason. I work with stone.", "저는 석공이에요. 돌을 다듬어요.");
			case "shepherd" -> of("I am a shepherd. I take care of sheep.", "저는 양치기예요. 양을 돌봐요.");
			case "toolsmith" -> of("I am a toolsmith. I make tools.", "저는 도구 대장장이예요. 도구를 만들어요.");
			case "weaponsmith" -> of("I am a weaponsmith. I make swords.", "저는 무기 대장장이예요. 검을 만들어요.");
			case "none" -> of("I do not have a job yet. I am looking for work.", "저는 아직 직업이 없어요. 일을 찾고 있어요.");
			case "nitwit" -> of("I do not have a job. I like to rest.", "저는 직업이 없어요. 쉬는 것을 좋아해요.");
			default -> of("I am a villager. I work in the village.", "저는 마을 주민이에요. 마을에서 일해요.");
		};
		return job.then("That is very cool.", "정말 멋있어요.");
	}

	static Line where(Place place) {
		Line answer = switch (place) {
			case WATER -> of("The water is next to the farm.", "물은 농장 옆에 있어요.");
			case FARM -> of("The farm is next to the water.", "농장은 물 옆에 있어요.");
			case BELL -> of("The bell is in the middle of the village.", "종은 마을 한가운데에 있어요.");
			case HOUSE -> of("My house is over there.", "제 집은 저기에 있어요.");
			case BED -> of("My bed is in my house.", "제 침대는 집 안에 있어요.");
			case VILLAGE -> of("You are in the village!", "여기가 마을이에요!");
			case UNKNOWN -> of("Sorry, I do not know.", "죄송해요, 잘 모르겠어요.");
		};
		return place == Place.UNKNOWN ? answer : answer.then("Thank you very much!", "정말 감사합니다!");
	}

	/** About the weather, as it really is. */
	static Line weather(Weather weather, boolean daylight, boolean likesIt) {
		return switch (weather) {
			case CLEAR -> daylight
					? (likesIt ? of("Yes, it is sunny. I like the sun.", "네, 날씨가 맑아요. 저는 해를 좋아해요.")
							: of("It is sunny. I like the sun.", "날씨가 맑아요. 저는 해를 좋아해요.")).then("I like the sun too.", "저도 해를 좋아해요.")
					: of("It is night, but the sky is clear. I like the stars.", "밤이지만 하늘이 맑아요. 저는 별을 좋아해요.")
							.then("I like the stars too.", "저도 별을 좋아해요.");
			case RAIN -> (likesIt ? of("Really? It is raining. I do not like the rain.", "정말요? 비가 와요. 저는 비를 안 좋아해요.")
					: of("It is raining today. I do not like the rain.", "오늘은 비가 와요. 저는 비를 안 좋아해요."))
					.then("I do not like the rain either.", "저도 비를 안 좋아해요.");
			case THUNDER -> of("There is a storm! I am scared of the thunder.", "폭풍이 와요! 천둥이 무서워요.").then("I am scared too.", "저도 무서워요.");
		};
	}

	/** The time of day, from 0 (sunrise) to 23999. */
	static Line time(int timeOfDay) {
		if (timeOfDay < 1_000) {
			return of("It is early in the morning. The sun is coming up.", "이른 아침이에요. 해가 뜨고 있어요.").then("Okay, thank you.", "알겠어요, 고마워요.");
		} else if (timeOfDay < 6_000) {
			return of("It is morning. It is a good time to work.", "아침이에요. 일하기 좋은 시간이에요.").then("Okay, thank you.", "알겠어요, 고마워요.");
		} else if (timeOfDay < 11_000) {
			return of("It is the afternoon. The sun is high.", "오후예요. 해가 높이 떠 있어요.").then("Okay, thank you.", "알겠어요, 고마워요.");
		} else if (timeOfDay < 13_000) {
			return of("It is getting dark. You should sleep soon.", "어두워지고 있어요. 곧 자야 해요.").then("Okay, good night.", "알겠어요, 잘 자요.");
		} else if (timeOfDay < 23_000) {
			return of("It is night. Zombies come out at night! You should sleep.", "밤이에요. 밤에는 좀비가 나와요! 자야 해요.")
					.then("Okay, good night.", "알겠어요, 잘 자요.");
		}

		return of("The sun is coming up soon.", "곧 해가 떠요.").then("Okay, thank you.", "알겠어요, 고마워요.");
	}

	static Line favoriteColor(boolean askBack) {
		return askBack
				? of("My favorite color is green, like an emerald. What is yours?", "제가 제일 좋아하는 색은 초록색이에요. 에메랄드처럼요! 어떤 색을 좋아해요?")
						.then("My favorite color is blue.", "제가 제일 좋아하는 색은 파란색이에요.")
				: of("My favorite color is green, like an emerald.", "제가 제일 좋아하는 색은 초록색이에요. 에메랄드처럼요!");
	}

	static Line niceColor(Color color) {
		if (color == Color.GREEN) {
			return of("Green? Me too!", "초록색이요? 저도요!");
		}

		String english = color.english();
		return of(Character.toUpperCase(english.charAt(0)) + english.substring(1) + " is a nice color!",
				color.korean() + Korean.topic(color.korean()) + " 예쁜 색이에요!");
	}

	static Line safety(boolean ironGolemNearby) {
		return ironGolemNearby
				? of("Yes, the Iron Golem protects us from zombies.", "네, 철 골렘이 좀비로부터 우리를 지켜 줘요.").then("That is good to know.", "다행이네요.")
				: of("I hope so. There is no Iron Golem here.", "그러면 좋겠어요. 여기는 철 골렘이 없어요.");
	}
}
