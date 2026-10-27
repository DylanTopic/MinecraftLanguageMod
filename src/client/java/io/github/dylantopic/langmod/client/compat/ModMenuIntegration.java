package io.github.dylantopic.langmod.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import io.github.dylantopic.langmod.client.config.LangModConfigScreen;

/** Adds the "Configure" button to LangMod's entry in Mod Menu. Only loaded when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LangModConfigScreen::new;
	}
}
