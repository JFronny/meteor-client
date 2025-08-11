/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface MeteorWidget extends BaseWidget {
    default MeteorGuiTheme theme() {
        return (MeteorGuiTheme) getTheme();
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
        MeteorGuiTheme theme = theme();
        double s = theme.scale(2);

        int r = theme.roundAmount();
        renderer.quadRounded(widget.x + s, widget.y + s, widget.width - s * 2, widget.height - s * 2, backgroundColor, r - s);
        renderer.quadOutlineRounded(widget, outlineColor, r, s);
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        MeteorGuiTheme theme = theme();
        renderBackground(renderer, widget, theme.outlineColor.get(pressed, mouseOver), theme.backgroundColor.get(pressed, mouseOver));
    }
}
