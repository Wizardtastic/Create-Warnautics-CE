package com.cbc_more_content.client.gui;

import com.cbc_more_content.block.MissileLaunchpadBlockEntity;
import com.cbc_more_content.network.LaunchpadSettingsPayload;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public class MissileLaunchpadScreen extends Screen {
    private static final int PANEL_WIDTH = 288;
    private static final int PANEL_HEIGHT = 200;
    private static final int COORDINATE_LIMIT = 30_000_000;

    private final BlockPos pos;
    private MissileLaunchpadBlockEntity.Mode mode;
    private boolean autoFire;
    private int targetX;
    private int targetY;
    private int targetZ;

    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button modeButton;
    private Button autoButton;

    private boolean hasMissile;

    @Nullable
    private BlockPos radarLink;

    private int left;
    private int top;
    private float age;

    public MissileLaunchpadScreen(
            BlockPos pos,
            MissileLaunchpadBlockEntity.Mode mode,
            BlockPos target,
            boolean autoFire,
            boolean hasMissile,
            @Nullable BlockPos radarLink) {
        super(Component.translatable("gui.cbc_more_content.launchpad.title"));
        this.pos = pos;
        this.mode = mode;
        this.autoFire = autoFire;
        this.targetX = target.getX();
        this.targetY = target.getY();
        this.targetZ = target.getZ();
        this.hasMissile = hasMissile;
        this.radarLink = radarLink;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;

        this.xBox = coordinateBox(this.left + 28, this.top + 48, "x", this.targetX);
        this.yBox = coordinateBox(this.left + 28, this.top + 68, "y", this.targetY);
        this.zBox = coordinateBox(this.left + 28, this.top + 88, "z", this.targetZ);

        this.modeButton = this.addRenderableWidget(Button.builder(modeLabel(), button -> {
                    this.mode = this.mode == MissileLaunchpadBlockEntity.Mode.COORDINATES
                            ? MissileLaunchpadBlockEntity.Mode.RADAR
                            : MissileLaunchpadBlockEntity.Mode.COORDINATES;
                    refreshControls();
                })
                .bounds(this.left + 118, this.top + 44, 156, 20)
                .build());
        this.autoButton = this.addRenderableWidget(Button.builder(autoLabel(), button -> {
                    this.autoFire = !this.autoFire;
                    this.autoButton.setMessage(autoLabel());
                })
                .bounds(this.left + 118, this.top + 68, 156, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(this.left + 14, this.top + 168, 80, 20)
                .build());
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.cbc_more_content.launchpad.apply"), button -> send(false))
                        .bounds(this.left + 98, this.top + 168, 80, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.cbc_more_content.launchpad.fire"), button -> send(true))
                        .bounds(this.left + 182, this.top + 168, 92, 20)
                        .build());

        refreshControls();
    }

    private EditBox coordinateBox(int x, int y, String axis, int value) {
        EditBox box = new EditBox(
                this.font,
                x,
                y,
                80,
                16,
                Component.translatable("gui.cbc_more_content.launchpad.coordinate", axis.toUpperCase()));
        box.setMaxLength(10);
        box.setValue(Integer.toString(value));
        box.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d{1,9}"));
        return this.addRenderableWidget(box);
    }

    private void refreshControls() {
        boolean coordinates = this.mode == MissileLaunchpadBlockEntity.Mode.COORDINATES;
        this.xBox.setEditable(coordinates);
        this.yBox.setEditable(coordinates);
        this.zBox.setEditable(coordinates);
        this.xBox.setTextColor(coordinates ? 0xE8E1CF : 0x5C6B72);
        this.yBox.setTextColor(coordinates ? 0xE8E1CF : 0x5C6B72);
        this.zBox.setTextColor(coordinates ? 0xE8E1CF : 0x5C6B72);
        this.modeButton.setMessage(modeLabel());
        this.autoButton.setMessage(autoLabel());
    }

    private Component modeLabel() {
        return Component.translatable(
                "gui.cbc_more_content.launchpad.mode",
                Component.translatable("gui.cbc_more_content.launchpad.mode."
                        + (this.mode == MissileLaunchpadBlockEntity.Mode.COORDINATES ? "coordinates" : "radar")));
    }

    private Component autoLabel() {
        return Component.translatable(
                "gui.cbc_more_content.launchpad.auto",
                Component.translatable("gui.cbc_more_content.launchpad." + (this.autoFire ? "on" : "off")));
    }

    private void send(boolean fire) {
        this.targetX = readCoordinate(this.xBox, this.targetX);
        this.targetY = readCoordinate(this.yBox, this.targetY);
        this.targetZ = readCoordinate(this.zBox, this.targetZ);
        PacketDistributor.sendToServer(new LaunchpadSettingsPayload(
                this.pos, this.mode.ordinal(), this.targetX, this.targetY, this.targetZ, this.autoFire, fire));
        this.onClose();
    }

    private static int readCoordinate(EditBox box, int fallback) {
        try {
            return Mth.clamp(Integer.parseInt(box.getValue().trim()), -COORDINATE_LIMIT, COORDINATE_LIMIT);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void tick() {
        this.age++;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(this.pos) instanceof MissileLaunchpadBlockEntity be)) {
            this.onClose();
            return;
        }
        this.hasMissile = be.hasMissile();
        this.radarLink = be.radarLink();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {
        int alpha = (int) (100 * Mth.clamp((this.age + partial) / 6, 0, 1));
        graphics.fillGradient(0, 0, this.width, this.height, alpha << 24 | 0x10151C, alpha << 24 | 0x04070C);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics, x, y, partial);
        graphics.fill(
                this.left - 2, this.top - 2, this.left + PANEL_WIDTH + 2, this.top + PANEL_HEIGHT + 2, 0xFF97785A);
        graphics.fill(this.left, this.top, this.left + PANEL_WIDTH, this.top + PANEL_HEIGHT, 0xFF15212B);
        graphics.fillGradient(
                this.left + 2, this.top + 2, this.left + PANEL_WIDTH - 2, this.top + 30, 0xFF354B57, 0xFF24333D);
        graphics.drawString(this.font, this.title, this.left + 14, this.top + 10, 0xF3E5C9, false);

        graphics.drawString(
                this.font,
                Component.translatable("gui.cbc_more_content.launchpad.coordinates"),
                this.left + 14,
                this.top + 36,
                0x92ADAF,
                false);
        graphics.drawString(this.font, "X", this.left + 16, this.top + 52, 0xC5B593, false);
        graphics.drawString(this.font, "Y", this.left + 16, this.top + 72, 0xC5B593, false);
        graphics.drawString(this.font, "Z", this.left + 16, this.top + 92, 0xC5B593, false);

        graphics.drawString(
                this.font,
                Component.translatable("gui.cbc_more_content.launchpad.radar"),
                this.left + 118,
                this.top + 33,
                0x92ADAF,
                false);
        Component missile = Component.translatable(
                "gui.cbc_more_content.launchpad.missile",
                Component.translatable("gui.cbc_more_content.launchpad." + (this.hasMissile ? "loaded" : "empty")));
        graphics.drawString(this.font, missile, this.left + 118, this.top + 96, 0xE8E1CF, false);
        Component link = this.radarLink == null
                ? Component.translatable("gui.cbc_more_content.launchpad.unlinked")
                : Component.translatable(
                        "gui.cbc_more_content.launchpad.linked",
                        this.radarLink.getX(),
                        this.radarLink.getY(),
                        this.radarLink.getZ());
        graphics.drawString(
                this.font, link, this.left + 118, this.top + 108, this.radarLink == null ? 0x87949B : 0x8FC3A5, false);
        graphics.drawWordWrap(
                this.font,
                Component.translatable("gui.cbc_more_content.launchpad.hint"),
                this.left + 118,
                this.top + 122,
                156,
                0xBDC4BA);
        graphics.fill(this.left + 14, this.top + 160, this.left + 274, this.top + 161, 0xFF425158);

        super.render(graphics, x, y, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
