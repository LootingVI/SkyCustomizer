package de.flori.skycustomizer.config;

import de.flori.skycustomizer.config.SkyCustomizerConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SkyCustomizerConfigScreen(parent);
    }
}
