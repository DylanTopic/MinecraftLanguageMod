package io.github.dylantopic.langmod.core.tts;

/**
 * A text-to-speech voice installed on the computer.
 *
 * @param id the engine's id for it
 * @param name e.g. "Microsoft Huihui"
 * @param locale BCP 47 tag of the language it speaks, e.g. "zh-CN"
 * @param gender "Female", "Male" or empty
 */
public record Voice(String id, String name, String locale, String gender) {
	/** "Microsoft Huihui (zh-CN)". */
	public String describe() {
		return name + " (" + locale + ")";
	}
}
