package io.github.dylantopic.langmod.core.talk;

import java.util.List;

/** Colors the player can name, with how they are said. The first name of each language is the one the villager uses. */
enum Color {
	RED(List.of("red"), List.of("빨간색", "빨강색", "빨강", "빨간", "붉은색")),
	ORANGE(List.of("orange"), List.of("주황색", "주황", "오렌지색")),
	YELLOW(List.of("yellow"), List.of("노란색", "노랑색", "노랑", "노란")),
	LIME(List.of("lime", "light green"), List.of("연두색", "연두")),
	GREEN(List.of("green"), List.of("초록색", "초록", "녹색")),
	LIGHT_BLUE(List.of("light blue", "sky blue"), List.of("하늘색")),
	NAVY(List.of("navy", "navy blue", "dark blue"), List.of("남색")),
	BLUE(List.of("blue"), List.of("파란색", "파랑색", "파랑", "파란")),
	PURPLE(List.of("purple", "violet"), List.of("보라색", "보라")),
	PINK(List.of("pink"), List.of("분홍색", "분홍", "핑크색", "핑크")),
	// Before silver: 검은색 contains 은색.
	BLACK(List.of("black"), List.of("검은색", "검정색", "검정", "까만색", "까만", "검은")),
	WHITE(List.of("white"), List.of("하얀색", "흰색", "하양", "하얀")),
	GRAY(List.of("gray", "grey"), List.of("회색")),
	BROWN(List.of("brown"), List.of("갈색")),
	CYAN(List.of("cyan", "turquoise"), List.of("청록색", "청록")),
	MAGENTA(List.of("magenta"), List.of("자홍색", "자홍")),
	GOLD(List.of("gold", "golden"), List.of("금색", "황금색")),
	SILVER(List.of("silver"), List.of("은색"));

	private final List<String> english;
	private final List<String> korean;

	Color(List<String> english, List<String> korean) {
		this.english = english;
		this.korean = korean;
	}

	String english() {
		return english.getFirst();
	}

	String korean() {
		return korean.getFirst();
	}

	/** The color named in English words padded with spaces ({@code " my favorite color is blue "}), or null. */
	static Color inEnglish(String padded) {
		// Two-word names first, so "light blue" is not read as blue.
		for (boolean twoWords : new boolean[] {true, false}) {
			for (Color color : values()) {
				for (String name : color.english) {
					if (name.contains(" ") == twoWords && padded.contains(" " + name + " ")) {
						return color;
					}
				}
			}
		}

		return null;
	}

	/** The color named in Korean written without spaces, or null. */
	static Color inKorean(String compact) {
		for (Color color : values()) {
			for (String name : color.korean) {
				if (compact.contains(name)) {
					return color;
				}
			}
		}

		return null;
	}
}
