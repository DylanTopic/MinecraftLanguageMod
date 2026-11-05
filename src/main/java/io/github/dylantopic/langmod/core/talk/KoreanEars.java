package io.github.dylantopic.langmod.core.talk;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.dylantopic.langmod.core.lang.Hangul;
import io.github.dylantopic.langmod.core.talk.Heard.Feeling;
import io.github.dylantopic.langmod.core.talk.Heard.Greeting;
import io.github.dylantopic.langmod.core.talk.Heard.Intent;
import io.github.dylantopic.langmod.core.talk.Heard.Place;
import io.github.dylantopic.langmod.core.talk.TalkState.Expecting;

/**
 * Korean: word stems are looked for in what was said with the spaces taken out, since speech
 * recognition (and learners) put spaces in different places ("몇 시" and "몇시"). Particles
 * after a word are stripped where a single word matters (물이 -> 물).
 */
final class KoreanEars implements Ears {
	private static final String[] QUESTION_PARTS = {"뭐", "무엇", "무슨", "어디", "어딨", "어떻게", "어때", "어떠", "언제", "누구", "왜", "몇", "어떤", "까요",
			"습니까", "합니까", "입니까"};
	/** Particles that can follow a noun, longest first. */
	private static final String[] PARTICLES = {"에서는", "에서", "에는", "은", "는", "이", "가", "을", "를", "에", "도", "의", "요"};
	/** Endings after a name ("딜런이에요" -> 딜런), longest first. */
	private static final String[] NAME_ENDINGS = {"이라고합니다", "라고합니다", "이라고해요", "라고해요", "이라고", "라고", "입니다", "이에요", "이예요", "예요", "에요",
			"이야", "이요"};
	/** Words ending in 물 that are not water. */
	private static final String[] NOT_WATER = {"동물", "건물", "선물", "괴물", "식물", "보물", "광물", "작물"};
	private static final String[][] PLACES = {
			{"호수", "WATER"}, {"바다", "WATER"}, {"연못", "WATER"}, {"우물", "WATER"}, {"냇물", "WATER"}, {"강물", "WATER"},
			{"농장", "FARM"}, {"작물", "FARM"}, {"밀밭", "FARM"}, {"침대", "BED"}, {"마을", "VILLAGE"}, {"주택", "HOUSE"},
			{"물", "WATER"}, {"강", "WATER"}, {"밭", "FARM"}, {"논", "FARM"}, {"종", "BELL"}, {"집", "HOUSE"}
	};

	@Override
	public Heard hear(String said, TalkState state) {
		List<String> words = words(said);
		String c = String.join("", words).toLowerCase(Locale.ROOT);
		Heard heard = new Heard();
		heard.question = said.contains("?") || said.contains("？") || any(c, QUESTION_PARTS);
		heard.agreeing = any(c, "저도", "나도", "마찬가지");

		if (any(c, "안녕히", "잘가", "잘있어", "잘계세요", "다음에봐", "나중에봐", "또봐", "또만나", "다음에또", "가야해", "가야돼", "가야되", "가야겠", "가볼게",
				"가볼께", "갈게요", "갈께요", "바이바이", "굿바이", "떠나야")) {
			heard.add(Intent.FAREWELL);
		}

		if (any(c, "잘자", "안녕히주무", "좋은꿈", "굿나잇", "편히쉬", "좋은밤")) {
			heard.add(Intent.GOOD_NIGHT);
		}

		if (!heard.has(Intent.FAREWELL)) {
			greeting(c, heard);
		}

		if (any(c, "제이름", "내이름", "저의이름", "나의이름") || any(c, "라고해요", "라고합니다", "라고불러")) {
			heard.name = name(words);

			if (heard.name != null || any(c, "제이름은", "내이름은")) {
				heard.add(Intent.INTRODUCE);
			}
		}

		if (any(c, "이름이뭐", "이름은뭐", "이름이무엇", "이름은무엇", "이름이어떻게", "이름은어떻게", "성함이", "성함은", "누구세요", "누구예요", "누구야", "누구십니까",
				"당신은누구", "이름은요", "너는누구", "이름알려", "이름이뭔")) {
			heard.add(Intent.ASK_NAME);
		}

		if (any(c, "반가워", "반갑", "만나서기뻐", "만나서좋")) {
			heard.add(Intent.NICE_TO_MEET);
		}

		if (!heard.has(Intent.FAREWELL) && (any(c, "어떻게지내", "기분이어때", "기분어때", "기분은어때", "기분이어떠", "잘지냈", "잘지내셨", "잘지내세요", "괜찮으세요",
				"별일없", "요즘어때", "오늘어때") || (heard.question && any(c, "잘지내", "괜찮아")))) {
			heard.add(Intent.ASK_FEELING);
		}

		heard.feeling = feeling(c);

		if (heard.feeling != null && (!heard.has(Intent.ASK_FEELING) || any(c, "저는", "나는")) && (state.expecting == Expecting.FEELING || heard.feeling == Feeling.HUNGRY
				|| any(c, "저는", "나는", "기분", "저도"))) {
			heard.add(Intent.FEELING);
		}

		if (any(c, "직업", "무슨일", "하는일", "일이뭐", "일은뭐", "뭐하세요", "뭐해요", "뭐하는", "무엇을하", "뭘하", "어떤일", "직장", "일하세요")) {
			heard.add(Intent.ASK_JOB);
		}

		if (any(c, "어디", "어딨", "어딘")) {
			heard.add(Intent.ASK_WHERE);
			heard.place = place(c);
		}

		if (any(c, "날씨", "맑", "화창", "햇빛", "햇볕", "쨍쨍", "비가", "비와", "비오", "비내", "폭풍", "천둥", "번개", "눈이와", "눈와", "눈이내", "흐려", "흐리",
				"흐림", "구름", "더워", "추워", "덥네", "춥네")) {
			heard.add(Intent.WEATHER);
			heard.likesWeather = any(c, "좋", "맑", "화창", "예쁘", "멋지", "따뜻", "최고", "아름다");
		}

		pay(c, words, heard, state);

		if (!heard.has(Intent.PAY) && any(c, "빵", "먹을거", "먹을것", "음식")) {
			heard.add(Intent.ASK_BREAD);
		} else if (!heard.has(Intent.PAY) && !heard.has(Intent.ASK_WHERE) && heard.question
				&& any(c, "있어요", "있나요", "있으세요", "있습니까", "있니", "파세요", "팔아요", "살수있", "사고싶", "주실수", "주세요")) {
			heard.add(Intent.ASK_OTHER);
		}

		if (any(c, "몇시", "지금시간", "시간이몇", "지금몇", "늦었", "시간알려", "시간이어떻게") || (heard.question && any(c, "밤이에요", "낮이에요", "밤인가", "낮인가"))) {
			heard.add(Intent.ASK_TIME);
		}

		heard.color = Color.inKorean(c);

		if (any(c, "좋아하는색", "무슨색", "어떤색", "색깔이뭐", "색이뭐", "색은뭐", "색깔은뭐", "색상이뭐")
				&& (heard.color == null || any(c, "뭐", "무엇", "무슨", "어떤"))) {
			heard.add(Intent.ASK_COLOR);
		}

		if (heard.color != null && (state.expecting == Expecting.COLOR || any(c, "좋아하는", "좋아해요", "좋아요", "제일좋", "가장좋", "좋아합니다"))) {
			heard.add(Intent.COLOR);
		}

		if (any(c, "안전", "위험", "좀비", "몬스터", "괴물", "약탈자", "습격", "지켜", "골렘", "공격")) {
			heard.add(Intent.SAFETY);
		}

		if (any(c, "고마워", "고맙", "감사", "땡큐")) {
			heard.add(Intent.THANKS);
		}

		if (any(c, "멋있", "멋지", "멋져", "대단", "훌륭", "최고", "좋네요", "예쁘", "신기", "재미있", "재밌", "근사", "와우") || words.contains("와")) {
			heard.add(Intent.COMPLIMENT);
		}

		if (heard.agreeing || anyWord(words, "네", "예", "응", "그래", "그래요", "맞아요", "맞아", "알겠어요", "알았어요", "오케이", "좋아요", "음")
				|| any(c, "알겠", "알았", "그렇군", "그렇구나", "그렇네", "다행", "좋은정보", "알게돼", "알게되", "그럼요", "물론")) {
			heard.add(Intent.AGREE);
		}

		return heard;
	}

	private static void greeting(String c, Heard heard) {
		if (c.contains("좋은아침")) {
			heard.greeting = Greeting.MORNING;
		} else if (c.contains("좋은오후")) {
			heard.greeting = Greeting.AFTERNOON;
		} else if (c.contains("좋은저녁")) {
			heard.greeting = Greeting.EVENING;
		} else if (!any(c, "안녕", "하이", "헬로", "여보세요")) {
			return;
		}

		heard.add(Intent.GREET);
	}

	private static Feeling feeling(String c) {
		if (any(c, "배고파", "배가고파", "배고프", "배가고프")) {
			return Feeling.HUNGRY;
		}

		if (any(c, "안좋", "피곤", "슬퍼", "슬프", "아파", "아프", "나빠", "무서워", "무섭", "힘들", "별로", "우울", "심심", "졸려", "외로", "걱정")) {
			return Feeling.BAD;
		}

		if (any(c, "잘지내", "좋아요", "좋습니다", "괜찮아", "괜찮습니다", "행복", "기뻐", "기쁘", "기분이좋", "최고", "잘있어요", "신나", "즐거")) {
			return Feeling.GOOD;
		}

		return null;
	}

	/** The place asked about: the noun just before 어디 ("물이 어디에"), or just after it ("어디에 물이"). */
	static Place place(String c) {
		int at = indexOfAny(c, "어디", "어딨", "어딘");
		Place before = placeAtEnd(withoutParticle(c.substring(0, at)));

		if (before != Place.UNKNOWN) {
			return before;
		}

		String after = c.substring(at + 2);

		for (String start : new String[] {"에서", "에", "로"}) {
			if (after.startsWith(start)) {
				after = after.substring(start.length());
				break;
			}
		}

		return placeAtStart(after);
	}

	private static Place placeAtEnd(String text) {
		for (String word : NOT_WATER) {
			if (text.endsWith(word)) {
				return word.equals("작물") ? Place.FARM : Place.UNKNOWN;
			}
		}

		for (String[] place : PLACES) {
			if (text.endsWith(place[0])) {
				return Place.valueOf(place[1]);
			}
		}

		return Place.UNKNOWN;
	}

	private static Place placeAtStart(String text) {
		for (String[] place : PLACES) {
			String rest = text.startsWith(place[0]) ? text.substring(place[0].length()) : null;

			if (rest != null && (rest.isEmpty() || startsWithAny(rest, "이", "가", "은", "는", "을", "를", "에", "도", "있", "좀", "요"))) {
				return Place.valueOf(place[1]);
			}
		}

		return Place.UNKNOWN;
	}

	private static String withoutParticle(String text) {
		for (String particle : PARTICLES) {
			if (text.endsWith(particle) && text.length() > particle.length()) {
				return text.substring(0, text.length() - particle.length());
			}
		}

		return text;
	}

	private static void pay(String c, List<String> words, Heard heard, TalkState state) {
		boolean emerald = any(c, "에메랄드", "에매랄드", "에메랄");
		boolean giving = any(c, "여기", "드릴게", "드릴께", "드려요", "드립니다", "받으세요", "줄게", "줄께", "낼게", "낼께", "주겠", "있어요", "있습니다");
		boolean yes = anyWord(words, "네", "예", "응", "그래", "그래요", "좋아요", "좋아", "알겠어요", "주세요", "살게요", "살래요", "여기요")
				|| any(c, "살게", "살래", "주세요", "알겠", "그럴게");

		if ((emerald && (giving || !heard.question)) || (state.expecting == Expecting.PAYMENT && (giving || yes))) {
			heard.add(Intent.PAY);
		} else if (state.expecting == Expecting.PAYMENT && (anyWord(words, "아니요", "아니", "아뇨", "아니에요")
				|| any(c, "아니요", "아뇨", "괜찮아요", "괜찮습니다", "됐어요", "안살", "필요없", "비싸"))) {
			heard.add(Intent.DECLINE);
		}
	}

	/**
	 * The name after 이름은 or 이름이 ("제 이름은 딜런이에요" -> 딜런), or before 라고 해요 ("딜런이라고 해요"), or null.
	 * Latin letters keep their case.
	 */
	static String name(List<String> words) {
		for (int i = 0; i < words.size(); i++) {
			String word = words.get(i);
			int at = Math.max(word.indexOf("이름은"), word.indexOf("이름이"));
			String candidate = null;

			if (at >= 0) {
				String rest = word.substring(at + 3);
				candidate = !rest.isEmpty() ? rest : i + 1 < words.size() ? words.get(i + 1) : null;
			} else if (word.endsWith("라고") && i + 1 < words.size() && startsWithAny(words.get(i + 1), "해", "합", "불러", "부릅")) {
				candidate = word;
			} else if (word.contains("라고해") || word.contains("라고합")) {
				candidate = word.substring(0, word.indexOf("라고")) + "라고";
			}

			String name = candidate == null ? null : cleanName(candidate);

			if (name != null) {
				return name;
			}
		}

		return null;
	}

	private static String cleanName(String candidate) {
		String name = candidate;

		for (String ending : NAME_ENDINGS) {
			if (name.endsWith(ending) && name.length() > ending.length()) {
				name = name.substring(0, name.length() - ending.length());
				break;
			}
		}

		if (name.isEmpty() || name.codePointCount(0, name.length()) > 12 || startsWithAny(name, "뭐", "무엇", "누구", "어떻", "뭔")) {
			return null;
		}

		boolean latin = name.chars().allMatch(ch -> ch < 0x80);
		return latin && name.equals(name.toLowerCase(Locale.ROOT)) ? name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) : name;
	}

	/** Words of Hangul, letters and digits; everything else separates words. */
	static List<String> words(String said) {
		String text = Normalizer.normalize(said, Normalizer.Form.NFC);
		List<String> words = new ArrayList<>();
		StringBuilder word = new StringBuilder();

		for (int cp : text.codePoints().toArray()) {
			if (Hangul.isSyllable(cp) || Character.isLetterOrDigit(cp)) {
				word.appendCodePoint(cp);
			} else if (!word.isEmpty()) {
				words.add(word.toString());
				word.setLength(0);
			}
		}

		if (!word.isEmpty()) {
			words.add(word.toString());
		}

		return words;
	}

	private static boolean any(String compact, String... stems) {
		for (String stem : stems) {
			if (compact.contains(stem)) {
				return true;
			}
		}

		return false;
	}

	private static boolean anyWord(List<String> words, String... wanted) {
		for (String word : wanted) {
			if (words.contains(word)) {
				return true;
			}
		}

		return false;
	}

	private static boolean startsWithAny(String text, String... starts) {
		for (String start : starts) {
			if (text.startsWith(start)) {
				return true;
			}
		}

		return false;
	}

	private static int indexOfAny(String text, String... parts) {
		int first = -1;

		for (String part : parts) {
			int at = text.indexOf(part);

			if (at >= 0 && (first < 0 || at < first)) {
				first = at;
			}
		}

		return first;
	}
}
