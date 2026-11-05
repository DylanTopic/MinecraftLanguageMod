package io.github.dylantopic.langmod.client.villager;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.client.voice.Pronouncer;
import io.github.dylantopic.langmod.core.audio.Vad;
import io.github.dylantopic.langmod.core.lang.KoreanRomanizer;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.talk.Phrase;
import io.github.dylantopic.langmod.core.talk.VillagerTalk;
import io.github.dylantopic.langmod.net.VillagerActionPayload;
import io.github.dylantopic.langmod.net.VillagerReplyPayload;
import io.github.dylantopic.langmod.net.VillagerSayPayload;

/**
 * A conversation with a villager: what was said so far, a box to type in, and Speak to say it
 * out loud (heard by Whisper, like item names). The villager's lines show their romanization and
 * English translation (as the challenge popup does) and are said out loud with the language's
 * voice; click one to hear it again. Phrases lists things to say; clicking one says it and puts
 * it in the box. Under the conversation, what could be said back.
 */
public class VillagerConversationScreen extends Screen {
	public static final String SPEAK = "langmod.villager.speak";
	public static final String SEND = "langmod.villager.send";
	public static final String PHRASES = "langmod.villager.phrases";
	private static final int MAX_WIDTH = 440;
	private static final int BUTTON_WIDTH = 56;
	/** How long Speak listens for the first word. */
	private static final int LISTEN_TICKS = 20 * 10;
	/** How long to wait for the villager's answer before giving up on it. */
	private static final int ANSWER_TICKS = 20 * 5;
	private static final int PLAYER = 0xFFFFFFFF;
	private static final int VILLAGER = 0xFFFFE6A0;
	private static final int PRONUNCIATION = 0xFF80D8E8;
	private static final int TRANSLATION = 0xFFA8A8B0;
	private static final int NOTE = 0xFF9898A4;
	private static final int STATUS = 0xFFFFD27A;
	private static final int SUGGESTION = 0xFF9FD89F;

	private enum Speaker {
		PLAYER,
		VILLAGER,
		/** A note from the game; its text is a translation key. */
		NOTE
	}

	private record Entry(Speaker speaker, String text, String translation) {
	}

	/** One line on screen. Clicking it says {@code speech}; for a phrase, also puts it in the box. */
	private record Row(FormattedCharSequence text, int color, int indent, String speech, Phrase phrase) {
	}

	private final int villagerId;
	private final Language language;
	private final List<Entry> history = new ArrayList<>();
	private final List<Phrase> examples;
	private final List<Row> rows = new ArrayList<>();
	private Phrase suggestion;
	private Component status;
	private String lastVillagerLine;
	private boolean showPhrases;
	private boolean over;
	private int waitingTicks;
	/** Lines scrolled back from the end. */
	private int scroll;
	private MicCapture mic;
	private boolean checking;
	private int listenTicks;

	private EditBox input;
	private Button speak;
	private Button send;
	private Button phrases;
	private int left;
	private int right;
	private int top;
	private int bottom;

	public VillagerConversationScreen(int villagerId, Language language) {
		super(Component.translatable("langmod.villager.title", Villagers.name(villagerId), language.englishName()));
		this.villagerId = villagerId;
		this.language = language;
		Minecraft minecraft = Minecraft.getInstance();
		this.examples = VillagerTalk.examples(language, minecraft.player == null ? "Steve" : minecraft.player.getName().getString());
	}

	@Override
	protected void init() {
		int width = Math.min(MAX_WIDTH, this.width - 24);
		left = (this.width - width) / 2;
		right = left + width;
		top = 24;
		bottom = this.height - 66;
		int inputY = this.height - 50;
		int buttonsY = this.height - 25;

		input = new EditBox(this.font, left, inputY, width - 2 * (BUTTON_WIDTH + 4), 20, input, Component.translatable("langmod.villager.input"));
		input.setMaxLength(VillagerSayPayload.MAX_LENGTH);
		input.setHint(Component.translatable("langmod.villager.input"));
		input.setResponder(text -> updateButtons());
		speak = Button.builder(Component.translatable(SPEAK), button -> toggleListening())
				.pos(right - 2 * BUTTON_WIDTH - 4, inputY).size(BUTTON_WIDTH, 20).build();
		send = Button.builder(Component.translatable(SEND), button -> sendTyped()).pos(right - BUTTON_WIDTH, inputY).size(BUTTON_WIDTH, 20).build();
		phrases = Button.builder(Component.translatable(PHRASES), button -> togglePhrases()).pos(this.width / 2 - 154, buttonsY).size(150, 20)
				.tooltip(Tooltip.create(Component.translatable("langmod.villager.phrases.tooltip"))).build();
		Button done = Button.builder(CommonComponents.GUI_DONE, button -> onClose()).pos(this.width / 2 + 4, buttonsY).size(150, 20).build();

		this.addRenderableWidget(input);
		this.addRenderableWidget(speak);
		this.addRenderableWidget(send);
		this.addRenderableWidget(phrases);
		this.addRenderableWidget(done);
		this.setInitialFocus(input);
		layout();
		updateButtons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 9, 0xFFFFFFFF);
		graphics.fill(left - 4, top - 4, right + 4, bottom + 3, 0xA0000000);
		int first = firstShownRow();
		int y = top;

		for (int i = first; i < Math.min(rows.size(), first + visibleRows()); i++) {
			Row row = rows.get(i);
			graphics.text(this.font, row.text(), left + row.indent(), y, row.color());
			y += lineHeight();
		}

		if (first + visibleRows() < rows.size() || first > 0) {
			graphics.text(this.font, Component.literal(first > 0 ? "▲" : "▼").getVisualOrderText(), right - 6, first > 0 ? top : bottom - lineHeight(), NOTE);
		}

		Component line = status != null ? status : suggestion != null && !showPhrases && !over ? suggestionLine() : null;

		if (line != null) {
			List<FormattedCharSequence> wrapped = this.font.split(line, right - left);
			graphics.text(this.font, wrapped.getFirst(), left, bottom + 6, status != null ? STATUS : SUGGESTION);
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (mic != null) {
			boolean villagerTalking = Pronouncer.get().busy();
			mic.setPaused(villagerTalking); // so it does not hear the villager's voice

			if (!villagerTalking && !mic.inSpeech() && --listenTicks <= 0) {
				closeMic();
				status = Component.translatable("langmod.villager.noSpeech");
				updateButtons();
			}
		}

		if (waitingTicks > 0 && --waitingTicks == 0) {
			status = Component.translatable("langmod.villager.noAnswer");
			updateButtons();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && !input.getValue().isBlank()) {
			sendTyped();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		Row row = rowAt(event.x(), event.y());

		if (row == null || row.speech() == null) {
			return false;
		}

		if (row.phrase() != null && !over) {
			input.setValue(row.phrase().text());
			showPhrases = false;
			layout();
			updateButtons();
			this.setFocused(input);
		}

		Pronouncer.get().sayText(language, row.speech());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.clamp(scroll + (scrollY > 0 ? 3 : scrollY < 0 ? -3 : 0), 0, Math.max(0, rows.size() - visibleRows()));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		closeMic();
		Villagers.send(new VillagerActionPayload(villagerId, VillagerActionPayload.Action.STOP));
		super.removed();
	}

	/** The server's answer (or that the villager walked away). */
	public void onReply(VillagerReplyPayload payload) {
		if (payload.villagerId() != villagerId) {
			return;
		}

		waitingTicks = 0;
		status = null;

		if (payload.kind() == VillagerReplyPayload.Kind.LEFT) {
			over = true;
			closeMic();
			history.add(new Entry(Speaker.NOTE, "langmod.villager.left", null));
		} else {
			history.add(new Entry(Speaker.VILLAGER, payload.text(), payload.translation()));
			lastVillagerLine = payload.text();
			suggestion = payload.suggestion();

			if (LangModConfig.get().villagerVoice) {
				Pronouncer.get().sayText(language, payload.text());
			}
		}

		scroll = 0;
		layout();
		updateButtons();
	}

	/** Says a line to the villager (also used by the game test). */
	public void sendLine(String text) {
		if (over || waitingTicks > 0 || text.isBlank()) {
			return;
		}

		history.add(new Entry(Speaker.PLAYER, text.strip(), null));
		waitingTicks = ANSWER_TICKS;
		suggestion = null;
		status = null;
		showPhrases = false;
		scroll = 0;
		Villagers.send(new VillagerSayPayload(villagerId, text.strip()));
		layout();
		updateButtons();
	}

	/** Treats a clip as if the microphone had recorded it (game tests). */
	public void submitClipForTesting(float[] samples) {
		transcribe(samples);
	}

	/** The villager's last line, or null. */
	public String lastVillagerLine() {
		return lastVillagerLine;
	}

	public int lines() {
		return history.size();
	}

	/** Whether a line was said and the villager has not answered yet. */
	public boolean waiting() {
		return waitingTicks > 0;
	}

	/** Whether the villager walked away. */
	public boolean over() {
		return over;
	}

	public Phrase suggestion() {
		return suggestion;
	}

	public int villagerId() {
		return villagerId;
	}

	private void sendTyped() {
		String text = input.getValue();

		if (!text.isBlank() && !over && waitingTicks == 0) {
			input.setValue("");
			sendLine(text);
		}
	}

	private void togglePhrases() {
		showPhrases = !showPhrases;
		scroll = 0;
		layout();
		updateButtons();
	}

	private void toggleListening() {
		if (mic != null) {
			closeMic();
			status = null;
			updateButtons();
			return;
		}

		SpeechService speech = SpeechService.get();

		if (!speech.ready()) {
			status = Component.translatable("langmod.villager.speechNotReady", speech.statusMessage());
			return;
		}

		try {
			mic = MicCapture.open(LangModConfig.get().micDevice, vadConfig(), listener());
		} catch (Exception e) {
			status = Component.translatable("langmod.villager.micError", String.valueOf(e.getMessage()));
			return;
		}

		listenTicks = LISTEN_TICKS;
		status = Component.translatable("langmod.villager.listening", language.englishName());
		updateButtons();
	}

	/** Like the item challenges, but sentences are longer and learners pause to think. */
	private static Vad.Config vadConfig() {
		LangModConfig config = LangModConfig.get();
		return new Vad.Config((float) config.vadThresholdDb, Math.max(config.vadSilenceMs, 1200), Math.max(config.vadMaxClipMs, 15_000),
				config.vadMinSpeechMs);
	}

	private MicCapture.Listener listener() {
		Minecraft minecraft = Minecraft.getInstance();
		return new MicCapture.Listener() {
			@Override
			public void onSpeechStart() {
				minecraft.execute(() -> {
					if (mic != null) {
						status = Component.translatable("langmod.villager.recording");
					}
				});
			}

			@Override
			public void onClip(float[] samples) {
				minecraft.execute(() -> transcribe(samples));
			}

			@Override
			public void onDiscarded() {
				minecraft.execute(() -> {
					if (mic != null) {
						status = Component.translatable("langmod.villager.listening", language.englishName());
					}
				});
			}

			@Override
			public void onError(String message) {
				minecraft.execute(() -> {
					closeMic();
					status = Component.translatable("langmod.villager.micError", message);
					updateButtons();
				});
			}
		};
	}

	private void transcribe(float[] samples) {
		if (checking || over) {
			return;
		}

		closeMic();
		checking = true;
		status = Component.translatable("langmod.villager.checking");
		updateButtons();
		SpeechService.get().transcribeWithPrompt(samples, language, lastVillagerLine).whenComplete((transcript, error) -> Minecraft.getInstance().execute(() -> {
			checking = false;

			if (error != null) {
				LangMod.LOGGER.warn("Transcription for the villager failed: {}", error.toString());
				status = Component.translatable("langmod.villager.notHeard");
			} else if (VillagerTalk.isNoise(language, transcript.text())) {
				status = Component.translatable("langmod.villager.notHeard");
			} else {
				sendLine(transcript.text());
			}

			updateButtons();
		}));
	}

	private void closeMic() {
		if (mic != null) {
			mic.close();
			mic = null;
		}
	}

	private void updateButtons() {
		if (speak == null) {
			return; // before init
		}

		speak.setMessage(Component.translatable(mic != null ? "langmod.villager.stop" : SPEAK));
		speak.active = !over && !checking && waitingTicks == 0;
		send.active = !over && !checking && waitingTicks == 0 && !input.getValue().isBlank();
		input.setEditable(!over);
		phrases.setMessage(Component.translatable(showPhrases ? "langmod.villager.back" : PHRASES));
	}

	/** Rebuilds the lines shown: the conversation, or the phrases. */
	private void layout() {
		rows.clear();

		if (showPhrases) {
			addRows(Component.translatable("langmod.villager.phrases.hint"), NOTE, 0, null, null);

			for (Phrase phrase : examples) {
				addPhrase(Component.literal("» " + phrase.text()), PLAYER, phrase.text(), phrase.translation(), phrase);
			}

			return;
		}

		if (history.isEmpty()) {
			addRows(Component.translatable("langmod.villager.start"), NOTE, 0, null, null);
		}

		for (Entry entry : history) {
			switch (entry.speaker()) {
				case PLAYER -> addRows(Component.translatable("langmod.villager.you").append(": ").withStyle(ChatFormatting.GRAY)
						.append(Component.literal(entry.text()).withStyle(ChatFormatting.WHITE)), PLAYER, 0, null, null);
				case VILLAGER -> {
					MutableComponent line = Villagers.name(villagerId).copy().append(": ").withStyle(ChatFormatting.GOLD);
					addPhrase(line.append(Component.literal(entry.text()).withStyle(ChatFormatting.YELLOW)), VILLAGER, entry.text(), entry.translation(), null);
				}
				case NOTE -> addRows(Component.translatable(entry.text()).withStyle(ChatFormatting.ITALIC), NOTE, 0, null, null);
			}
		}
	}

	/** A line in the language, with its romanization and translation under it as settings say. */
	private void addPhrase(Component text, int color, String speech, String translation, Phrase phrase) {
		LangModConfig config = LangModConfig.get();
		addRows(text, color, 0, speech, phrase);

		if (language == Language.KOREAN && config.showPronunciation) {
			addRows(Component.literal(KoreanRomanizer.romanize(speech)), PRONUNCIATION, 10, speech, phrase);
		}

		if (translation != null && config.showEnglish) {
			addRows(Component.literal(translation), TRANSLATION, 10, speech, phrase);
		}
	}

	private void addRows(Component text, int color, int indent, String speech, Phrase phrase) {
		for (FormattedCharSequence line : this.font.split(text, right - left - indent - 8)) {
			rows.add(new Row(line, color, indent, speech, phrase));
		}
	}

	private Component suggestionLine() {
		return Component.translatable("langmod.villager.try", suggestion.text());
	}

	private Row rowAt(double x, double y) {
		if (x < left || x > right || y < top || y >= top + visibleRows() * lineHeight()) {
			return null;
		}

		int index = firstShownRow() + (int) ((y - top) / lineHeight());
		return index < rows.size() ? rows.get(index) : null;
	}

	private int firstShownRow() {
		return Math.max(0, rows.size() - visibleRows() - scroll);
	}

	private int visibleRows() {
		return Math.max(1, (bottom - top) / lineHeight());
	}

	private int lineHeight() {
		return this.font.lineHeight + 1;
	}
}
