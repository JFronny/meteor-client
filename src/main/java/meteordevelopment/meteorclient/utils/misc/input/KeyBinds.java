/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc.input;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(MeteorClient.identifier("meteor-client"));

    public static KeyBinding OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.meteor-client.open-gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

    public static int getKey(KeyBinding bind) {
        return ((KeyBindingAccessor) bind).meteor$getKey().getCode();
    }

    public static void ensureInitialized() {
    }
}
