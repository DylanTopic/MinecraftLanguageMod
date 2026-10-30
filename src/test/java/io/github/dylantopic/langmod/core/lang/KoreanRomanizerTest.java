package io.github.dylantopic.langmod.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KoreanRomanizerTest {
	/** Examples from the Revised Romanization rules and Minecraft item names. */
	private static final String[][] EXAMPLES = {
			{"사과", "sagwa"},
			{"흙", "heuk"},
			{"활", "hwal"},
			{"빵", "ppang"},
			{"유리병", "yuribyeong"},
			{"다이아몬드 곡괭이", "daiamondeu gokgwaengi"},
			{"초록색 양털", "choroksaek yangteol"},
			{"마법이 부여된 황금 사과", "mabeobi buyeodoen hwanggeum sagwa"},
			{"신라", "silla"},
			{"설날", "seollal"},
			{"종로", "jongno"},
			{"독립", "dongnip"},
			{"왕십리", "wangsimni"},
			{"백마", "baengma"},
			{"국물", "gungmul"},
			{"합니다", "hamnida"},
			{"해돋이", "haedoji"},
			{"같이", "gachi"},
			{"좋고", "joko"},
			{"놓다", "nota"},
			{"많다", "manta"},
			{"축하", "chuka"},
			{"입학", "ipak"},
			{"좋아", "joa"},
			{"읽어", "ilgeo"},
			{"없어", "eopseo"},
			{"앉아", "anja"},
			{"학교", "hakgyo"},
			{"TNT", "TNT"}
	};

	@Test
	void followsTheRevisedRomanization() {
		for (String[] example : EXAMPLES) {
			assertEquals(example[1], KoreanRomanizer.romanize(example[0]), example[0]);
		}
	}
}
