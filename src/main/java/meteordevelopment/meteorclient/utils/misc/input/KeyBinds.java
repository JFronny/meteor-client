/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc.input;

import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MeteorClient.identifier("meteor-client"));

    public static KeyMapping OPEN_GUI = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.meteor-client.open-gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

    public static int getKey(KeyMapping bind) {
        return ((KeyMappingAccessor) bind).meteor$getKey().getValue();
    }

    public static void ensureInitialized() {
    }
}
