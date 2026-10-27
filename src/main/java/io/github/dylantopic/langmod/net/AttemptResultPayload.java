package io.github.dylantopic.langmod.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.core.Matcher;

/**
 * Server -> client: the verdict on one answer, with what was heard and which syllables matched.
 *
 * @param raw Whisper's raw output
 * @param heard the normalized (simplified) transcription
 * @param heardTokens heard characters with their pinyin and status
 * @param expectedStatus status of each expected syllable
 * @param note extra explanation (e.g. why an answer was not counted)
 */
public record AttemptResultPayload(int challengeId, Verdict verdict, String raw, String heard, List<Token> heardTokens,
		List<Matcher.Status> expectedStatus, int errors, int allowedErrors, String mode, int millis, int attemptsUsed,
		String note) implements CustomPacketPayload {
	public static final Type<AttemptResultPayload> TYPE = new Type<>(LangMod.id("attempt_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AttemptResultPayload> CODEC =
			CustomPacketPayload.codec(AttemptResultPayload::write, AttemptResultPayload::new);

	public enum Verdict {
		CORRECT,
		/** Counted as a wrong attempt. */
		WRONG,
		/** Nothing (or only noise) was heard: not counted. */
		NO_SPEECH,
		/** Transcription failed or timed out: not counted. */
		ERROR
	}

	public record Token(String text, String pinyin, Matcher.Status status) {
	}

	private AttemptResultPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), Verdict.values()[buf.readVarInt()], buf.readUtf(), buf.readUtf(), readTokens(buf), readStatuses(buf),
				buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(challengeId);
		buf.writeVarInt(verdict.ordinal());
		buf.writeUtf(raw);
		buf.writeUtf(heard);
		buf.writeVarInt(heardTokens.size());

		for (Token token : heardTokens) {
			buf.writeUtf(token.text());
			buf.writeUtf(token.pinyin());
			buf.writeVarInt(token.status().ordinal());
		}

		buf.writeVarInt(expectedStatus.size());

		for (Matcher.Status status : expectedStatus) {
			buf.writeVarInt(status.ordinal());
		}

		buf.writeVarInt(errors);
		buf.writeVarInt(allowedErrors);
		buf.writeUtf(mode);
		buf.writeVarInt(millis);
		buf.writeVarInt(attemptsUsed);
		buf.writeUtf(note);
	}

	private static List<Token> readTokens(RegistryFriendlyByteBuf buf) {
		int count = Math.min(buf.readVarInt(), 256);
		List<Token> tokens = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			tokens.add(new Token(buf.readUtf(), buf.readUtf(), Matcher.Status.values()[buf.readVarInt()]));
		}

		return tokens;
	}

	private static List<Matcher.Status> readStatuses(RegistryFriendlyByteBuf buf) {
		int count = Math.min(buf.readVarInt(), 256);
		List<Matcher.Status> statuses = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			statuses.add(Matcher.Status.values()[buf.readVarInt()]);
		}

		return statuses;
	}

	public static AttemptResultPayload of(int challengeId, Verdict verdict, String raw, Matcher.Result result, int millis,
			int attemptsUsed, String note) {
		List<Token> tokens = new ArrayList<>();

		for (Matcher.HeardToken token : result.heardTokens()) {
			tokens.add(new Token(token.text(), token.pinyin(), token.status()));
		}

		return new AttemptResultPayload(challengeId, verdict, raw, result.heard(), tokens, result.expectedStatus(),
				result.errors(), result.allowedErrors(), result.mode().id(), millis, attemptsUsed, note);
	}

	public static AttemptResultPayload notCounted(int challengeId, Verdict verdict, String raw, String heard, int millis,
			int attemptsUsed, String note) {
		return new AttemptResultPayload(challengeId, verdict, raw, heard, List.of(), List.of(), 0, 0, "", millis, attemptsUsed, note);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
