package com.mrh0.createaddition.compat.emi;

import com.mrh0.createaddition.blocks.tesla_coil.TeslaCoilBlock;
import com.mrh0.createaddition.index.CABlocks;
import com.zurrtum.create.client.catnip.gui.element.GuiGameElement;
import com.zurrtum.create.client.catnip.gui.element.GuiGameElement.GuiBlockStateRenderBuilder;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.client.foundation.gui.render.BasinBlazeBurnerRenderState;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3x2f;

import java.util.List;

/**
 * Shared pieces of the EMI pages: Create's JEI textures, the machines drawn as blocks, and stack conversion.
 */
final class CAEmiWidgets {
    static final int WIDTH = 177;
    static final int HEIGHT = 53;
    static final int TEXT_COLOR = 0xFFFFFFFF;
    // Create Fly keeps fluid amounts in Fabric droplets; EMI on NeoForge counts millibuckets
    private static final int DROPLETS_PER_MB = 81;
    // A machine is drawn MACHINE_SCALE * 16 px wide, centred in a box MACHINE_PADDING px wider so that the
    // rotated block's corners still fit
    private static final float MACHINE_SCALE = 1.375f;
    private static final int MACHINE_PADDING = 12;
    private static final int MACHINE_BOX = (int) (MACHINE_SCALE * 16) + MACHINE_PADDING;

    // Create caches one GPU texture per builder, so each machine keeps one builder for all of its pages
    private static GuiBlockStateRenderBuilder teslaCoil;
    private static GuiBlockStateRenderBuilder rollingMill;

    private CAEmiWidgets() {
    }

    static void texture(WidgetHolder widgets, AllGuiTextures texture, int x, int y) {
        widgets.addDrawable(x, y, texture.getWidth(), texture.getHeight(),
            (graphics, mouseX, mouseY, delta) -> texture.render(graphics, 0, 0));
    }

    /** The machine between input and output, where upstream's JEI categories put it. */
    static void machine(WidgetHolder widgets, boolean isTeslaCoil) {
        texture(widgets, AllGuiTextures.JEI_ARROW, 85, 32);
        texture(widgets, AllGuiTextures.JEI_DOWN_ARROW, 43, 4);
        texture(widgets, AllGuiTextures.JEI_SHADOW, 32, 40);
        widgets.addDrawable(57 - MACHINE_BOX / 2, 33 - MACHINE_BOX / 2, MACHINE_BOX, MACHINE_BOX,
            (graphics, mouseX, mouseY, delta) -> (isTeslaCoil ? teslaCoil() : rollingMill()).render(graphics));
    }

    static void blazeBurner(WidgetHolder widgets, HeatLevel heat, int x, int y) {
        widgets.addDrawable(x, y, 30, 30, (graphics, mouseX, mouseY, delta) -> graphics.guiRenderState
            .addPicturesInPictureState(new BasinBlazeBurnerRenderState(new Matrix3x2f(graphics.pose()), 0, 0, heat)));
    }

    private static GuiBlockStateRenderBuilder teslaCoil() {
        if (teslaCoil == null) {
            teslaCoil = machineBuilder(CABlocks.TESLA_COIL.defaultBlockState()
                .setValue(TeslaCoilBlock.FACING, Direction.DOWN)
                .setValue(TeslaCoilBlock.POWERED, true));
        }
        return teslaCoil;
    }

    private static GuiBlockStateRenderBuilder rollingMill() {
        if (rollingMill == null) {
            rollingMill = machineBuilder(CABlocks.ROLLING_MILL.defaultBlockState());
        }
        return rollingMill;
    }

    private static GuiBlockStateRenderBuilder machineBuilder(BlockState state) {
        return GuiGameElement.of(state)
            .rotate(-22.5f, 22.5f, 0)
            .scale(MACHINE_SCALE)
            .padding(MACHINE_PADDING);
    }

    static EmiStack output(ProcessingOutput output) {
        EmiStack stack = EmiStack.of(output.create());
        if (output.chance() < 1) stack.setChance(output.chance());
        return stack;
    }

    /** Every fluid the ingredient matches, at its amount in millibuckets. */
    static EmiIngredient fluid(List<FluidStack> fluids, int droplets) {
        long millibuckets = Math.max(1, (droplets + DROPLETS_PER_MB - 1) / DROPLETS_PER_MB);
        return EmiIngredient.of(fluids.stream()
            .map(fluid -> EmiStack.of(fluid.getFluid(), fluid.getComponentChanges(), millibuckets))
            .toList());
    }
}
