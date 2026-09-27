package com.anchorbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AnchorRtpBotClient implements ClientModInitializer {
    public static final String MOD_ID = "anchor-rtp-bot";
    private static final KeyMapping TOGGLE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.anchor_rtp_bot.toggle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    KeyMapping.Category.register(Identifier.of(MOD_ID, "main"))
            )
    );

    private AnchorBot bot;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        while (TOGGLE.wasPressed()) {
            if (bot == null) bot = new AnchorBot(client);
            bot.toggle();
        }
        if (bot != null) bot.tick();
    }

    public static void message(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[AnchorBot] " + text), false);
        }
    }
}
