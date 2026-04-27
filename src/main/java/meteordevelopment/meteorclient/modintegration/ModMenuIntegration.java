/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.modintegration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
    public static Screen getMeteorScreen(Screen parent) { // see also ModMenuCompat in MeteorAdditions
        GuiTheme theme = GuiThemes.get();
        ModulesScreen screen = new ModulesScreen(theme);
        screen.addDirect(theme.topBar()).top().centerX();
        screen.parent = parent;
        return screen;
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::getMeteorScreen;
    }
}
