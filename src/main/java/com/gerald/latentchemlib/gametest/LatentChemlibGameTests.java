package com.gerald.latentchemlib.gametest;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.blockentity.LatentMachineBlockEntity;
import com.gerald.latentchemlib.sim.ChemicalState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LatentChemlibMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LatentChemlibGameTests {
    private LatentChemlibGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void registeredBlocksCreateExpectedBlockEntities(GameTestHelper helper) {
        BlockPos cloudPos = new BlockPos(1, 1, 1);
        helper.setBlock(cloudPos, LatentChemlibMod.CHEMICAL_CLOUD.get());
        helper.assertTrue(helper.getBlockEntity(cloudPos) instanceof ChemicalCloudBlockEntity, "Chemical cloud should create its block entity");

        assertMachineEntity(helper, new BlockPos(2, 1, 1), LatentChemlibMod.GAS_CAPTURE.get());
        assertMachineEntity(helper, new BlockPos(3, 1, 1), LatentChemlibMod.GAS_TANK.get());
        assertMachineEntity(helper, new BlockPos(4, 1, 1), LatentChemlibMod.GAS_REACTION_CHAMBER.get());
        assertMachineEntity(helper, new BlockPos(5, 1, 1), LatentChemlibMod.GAS_RELEASE.get());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void chemicalCloudSeedMergeExtractAndRejectsDifferentChemicals(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChemicalCloudBlockEntity cloud = placeCloud(helper, pos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 100.0, 1.0, 400.0, 0.2, 20.0));
        cloud.seed(new ChemicalState("chemlib:hydrogen", 300.0, 3.0, 800.0, 0.6, 60.0));

        ChemicalState merged = cloud.chemicalState();
        helper.assertTrue(merged.mass() == 400.0, "Matching chemical clouds should merge mass");
        helper.assertTrue(merged.temperature() == 700.0, "Merged cloud should weight temperature by mass");

        cloud.seed(new ChemicalState("chemlib:helium", 1_000.0, 10.0, 100.0, 0.0, 0.0));
        helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:hydrogen"), "Different chemicals should not overwrite an occupied cloud");
        helper.assertTrue(cloud.chemicalState().mass() == 400.0, "Rejected chemicals should not alter mass");

        ChemicalState extracted = cloud.extractMass(150.0);
        helper.assertTrue(extracted.mass() == 150.0, "Extracted cloud state should cap to requested mass");
        helper.assertTrue(cloud.chemicalState().mass() == 250.0, "Cloud should retain remaining mass after extraction");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasCapturePullsMatterFromAdjacentCloud(GameTestHelper helper) {
        BlockPos capturePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = new BlockPos(2, 1, 1);
        LatentMachineBlockEntity capture = placeMachine(helper, capturePos, LatentChemlibMod.GAS_CAPTURE.get());
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 1_000.0, 4.0, 600.0, 0.4, 200.0));

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(capture.storedState().mass() == 250.0, "Gas capture should pull 25% of a 1000 mass cloud, capped at 250");
            helper.assertTrue(totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) == 750.0, "Captured matter should remain conserved across nearby cloud diffusion");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasReleaseCreatesCloudAboveAndConsumesStorage(GameTestHelper helper) {
        BlockPos releasePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = releasePos.above();
        LatentMachineBlockEntity release = placeMachine(helper, releasePos, LatentChemlibMod.GAS_RELEASE.get());
        release.setStoredState(new ChemicalState("chemlib:helium", 300.0, 2.0, 500.0, 0.1, 120.0));

        helper.runAfterDelay(25, () -> {
            ChemicalCloudBlockEntity cloud = cloudAt(helper, cloudPos);
            helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:helium"), "Gas release should seed a matching cloud above itself");
            helper.assertTrue(cloud.chemicalState().mass() == 250.0, "Gas release should move at most 250 mass per tick");
            helper.assertTrue(release.storedState().mass() == 50.0, "Gas release should retain unmoved storage");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void reactionChamberAgitatesStoredMatter(GameTestHelper helper) {
        BlockPos chamberPos = new BlockPos(1, 1, 1);
        LatentMachineBlockEntity chamber = placeMachine(helper, chamberPos, LatentChemlibMod.GAS_REACTION_CHAMBER.get());
        chamber.setStoredState(new ChemicalState("chemlib:hydrogen", 125.0, 1.0, 300.0, 0.0, 25.0));

        helper.runAfterDelay(25, () -> {
            ChemicalState state = chamber.storedState();
            helper.assertTrue(state.temperature() == 335.0, "Reaction chamber should heat stored matter");
            helper.assertTrue(state.charge() == 0.025, "Reaction chamber should increase charge");
            helper.assertTrue(state.energy() == 105.0, "Reaction chamber should add energy");
            helper.succeed();
        });
    }

    private static void assertMachineEntity(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        helper.assertTrue(helper.getBlockEntity(pos) instanceof LatentMachineBlockEntity, block.getDescriptionId() + " should create a latent machine entity");
    }

    private static ChemicalCloudBlockEntity placeCloud(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get());
        return cloudAt(helper, pos);
    }

    private static ChemicalCloudBlockEntity cloudAt(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (blockEntity instanceof ChemicalCloudBlockEntity cloud) return cloud;
        throw new IllegalStateException("Expected chemical cloud at " + pos);
    }

    private static LatentMachineBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (blockEntity instanceof LatentMachineBlockEntity machine) return machine;
        throw new IllegalStateException("Expected latent machine at " + pos);
    }

    private static double totalCloudMass(GameTestHelper helper, BlockPos from, BlockPos to) {
        double mass = 0.0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof ChemicalCloudBlockEntity cloud) {
                mass += cloud.chemicalState().mass();
            }
        }
        return mass;
    }
}
