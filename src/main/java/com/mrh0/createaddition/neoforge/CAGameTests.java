package com.mrh0.createaddition.neoforge;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.blocks.alternator.AlternatorBlockEntity;
import com.mrh0.createaddition.blocks.electric_motor.ElectricMotorBlockEntity;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.index.CAItems;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.base.DirectionalKineticBlock;
import com.zurrtum.create.content.kinetics.base.HorizontalKineticBlock;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

/**
 * ZP5 smoke tests for the NeoForge port, run by {@code runGameTestServer}. They use Create's empty structure
 * (data/create/structure/gametest/empty_9x7x5.nbt) and reach Crafts &amp; Additions' energy only through NeoForge's
 * {@code Capabilities.Energy}, the way Pipez or AE2 would.
 */
public final class CAGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(
        Registries.TEST_FUNCTION,
        CreateAddition.MODID
    );
    private static final ResourceKey<Consumer<GameTestHelper>> ALTERNATOR = key("alternator_generates_fe");
    private static final ResourceKey<Consumer<GameTestHelper>> ELECTRIC_MOTOR = key("electric_motor_runs_on_fe");
    private static final ResourceKey<Consumer<GameTestHelper>> ROLLING_MILL = key("rolling_mill_rolls_iron_rods");

    static {
        FUNCTIONS.register(ALTERNATOR.identifier().getPath(), () -> CAGameTests::alternatorGeneratesFe);
        FUNCTIONS.register(ELECTRIC_MOTOR.identifier().getPath(), () -> CAGameTests::electricMotorRunsOnFe);
        FUNCTIONS.register(ROLLING_MILL.identifier().getPath(), () -> CAGameTests::rollingMillRollsIronRods);
    }

    private CAGameTests() {
    }

    private static ResourceKey<Consumer<GameTestHelper>> key(String path) {
        return ResourceKey.create(Registries.TEST_FUNCTION, CreateAddition.asResource(path));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(CAGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(CreateAddition.asResource("default"));
        Identifier structure = Identifier.fromNamespaceAndPath(Create.MOD_ID, "gametest/empty_9x7x5");
        for (ResourceKey<Consumer<GameTestHelper>> test : new ResourceKey[]{ALTERNATOR, ELECTRIC_MOTOR, ROLLING_MILL}) {
            event.registerTest(test.identifier(), new FunctionGameTestInstance(test, new TestData<>(environment, structure, 400, 0, true)));
        }
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * A creative motor at 64 RPM turns an alternator (it needs Create's medium speed, 30 RPM by default). The alternator
     * pushes FE into a modular accumulator beside it, and a stand-in for another mod's energy pipe pulls FE out of the
     * accumulator's top face through NeoForge's energy capability.
     */
    private static void alternatorGeneratesFe(GameTestHelper helper) {
        floor(helper);
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos alternator = new BlockPos(3, 1, 2);
        BlockPos accumulator = new BlockPos(3, 1, 3);
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(alternator, CABlocks.ALTERNATOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        helper.setBlock(accumulator, CABlocks.MODULAR_ACCUMULATOR);
        helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);

        long[] pulled = {0};
        helper.onEachTick(() -> {
            EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(accumulator), Direction.UP);
            if (handler == null) return;
            try (Transaction t = Transaction.openRoot()) {
                pulled[0] += handler.extract(10, t);
                t.commit();
            }
        });
        helper.succeedWhen(() -> {
            AlternatorBlockEntity alternatorBe = helper.getBlockEntity(alternator, AlternatorBlockEntity.class);
            EnergyHandler stored = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(accumulator), Direction.UP);
            long inAccumulator = stored == null ? -1 : stored.getAmountAsLong();
            EnergyHandler fromAlternator = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(alternator), Direction.UP);
            String state = "alternator speed " + alternatorBe.getSpeed() + ", rate " + AlternatorBlockEntity.getEnergyProductionRate((int) alternatorBe.getSpeed())
                + " FE/t, alternator holds " + alternatorBe.getEnergyStorage(null).getAmount() + "/" + alternatorBe.getEnergyStorage(null).getCapacity()
                + " (capability " + (fromAlternator == null ? "missing" : "present") + "), accumulator " + inAccumulator + "/"
                + (stored == null ? -1 : stored.getCapacityAsLong()) + " FE, pulled through the capability " + pulled[0] + " FE";
            helper.assertTrue(alternatorBe.getSpeed() != 0 && inAccumulator >= 500 && pulled[0] >= 100, state);
            CreateAddition.LOGGER.info("alternator_generates_fe passed at tick {}: {}", helper.getTick(), state);
        });
    }

    /**
     * Another mod's energy pipe, stood in for by NeoForge's energy capability, feeds an electric motor, which must
     * start and turn the shaft in front of it.
     */
    private static void electricMotorRunsOnFe(GameTestHelper helper) {
        floor(helper);
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos shaft = new BlockPos(3, 1, 2);
        helper.setBlock(motor, CABlocks.ELECTRIC_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(shaft, AllBlocks.SHAFT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));

        long[] pushed = {0};
        helper.onEachTick(() -> {
            EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(motor), Direction.WEST);
            if (handler == null) return;
            try (Transaction t = Transaction.openRoot()) {
                pushed[0] += handler.insert(1000, t);
                t.commit();
            }
        });
        helper.succeedWhen(() -> {
            ElectricMotorBlockEntity motorBe = helper.getBlockEntity(motor, ElectricMotorBlockEntity.class);
            KineticBlockEntity shaftBe = helper.getBlockEntity(shaft, KineticBlockEntity.class);
            String state = "pushed " + pushed[0] + " FE, motor " + motorBe.getSpeed() + " RPM using " + motorBe.getEnergyConsumption()
                + " FE/t, shaft " + shaftBe.getSpeed() + " RPM";
            helper.assertTrue(pushed[0] > 0 && motorBe.getSpeed() != 0 && shaftBe.getSpeed() != 0, state);
            CreateAddition.LOGGER.info("electric_motor_runs_on_fe passed at tick {}: {}", helper.getTick(), state);
        });
    }

    /**
     * A creative motor turns a rolling mill. An iron ingot goes in and the rods come out through NeoForge's item
     * capability, the way a hopper, Pipez or AE2 would move them. The recipe uses the c:ingots/iron tag.
     */
    private static void rollingMillRollsIronRods(GameTestHelper helper) {
        floor(helper);
        BlockPos motor = new BlockPos(2, 1, 2);
        BlockPos mill = new BlockPos(3, 1, 2);
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(mill, CABlocks.ROLLING_MILL.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        helper.getBlockEntity(motor, CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);

        int[] moved = {0, 0};
        helper.onEachTick(() -> {
            ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(mill), Direction.UP);
            if (handler == null) return;
            try (Transaction t = Transaction.openRoot()) {
                if (moved[0] == 0) moved[0] += handler.insert(ItemResource.of(Items.IRON_INGOT), 1, t);
                moved[1] += handler.extract(ItemResource.of(CAItems.IRON_ROD), 64, t);
                t.commit();
            }
        });
        helper.succeedWhen(() -> {
            KineticBlockEntity millBe = helper.getBlockEntity(mill, KineticBlockEntity.class);
            String state = "inserted " + moved[0] + " iron ingot, extracted " + moved[1] + " iron rods, mill " + millBe.getSpeed() + " RPM; "
                + count(BuiltInRegistries.BLOCK) + " blocks, " + count(BuiltInRegistries.ITEM) + " items, "
                + count(BuiltInRegistries.BLOCK_ENTITY_TYPE) + " block entity types registered";
            helper.assertTrue(moved[0] == 1 && moved[1] >= 2, state);
            CreateAddition.LOGGER.info("rolling_mill_rolls_iron_rods passed at tick {}: {}", helper.getTick(), state);
        });
    }

    private static long count(Registry<?> registry) {
        return registry.keySet().stream().filter(id -> id.getNamespace().equals(CreateAddition.MODID)).count();
    }
}
