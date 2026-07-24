package com.zenithclient.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Locale;

/** X-Ray block visibility decisions for ZenithClient. */
public final class XrayHooks {
    private XrayHooks() { }

    public static int alpha(BlockState state, BlockPos pos) {
        ZenithConfig config = ZenithClient.getConfig();
        if (!config.xray) return -1;
        Block block = state.getBlock();
        if (block == Blocks.AIR) return -1;

        // Real renderer X-ray: selected blocks keep their normal render path;
        // every other solid block is removed from chunk meshes.
        return isBlocked(block) ? 0 : -1;
    }

    public static boolean isBlocked(Block block) {
        return !isWhitelisted(block);
    }

    /** Reveal selected block faces beside hidden or transparent blocks. */
    public static boolean modifyDrawSide(BlockState state, BlockGetter view, BlockPos pos, Direction facing, boolean vanilla) {
        if (!ZenithClient.getConfig().xray) return vanilla;
        if (vanilla || isBlocked(state.getBlock())) return vanilla;

        BlockPos adjacentPos = pos.relative(facing);
        BlockState adjacent = view.getBlockState(adjacentPos);
        return adjacent.getFaceOcclusionShape(facing.getOpposite()) != Shapes.block()
                || adjacent.getBlock() != state.getBlock()
                || !adjacent.isSolidRender()
                || isBlocked(adjacent.getBlock());
    }

    public static boolean isWhitelisted(Block block) {
        if (block == Blocks.WATER || block == Blocks.LAVA) return true;
        String search = ZenithClient.getConfig().xraySearch == null ? "" : ZenithClient.getConfig().xraySearch.trim().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString().toLowerCase(Locale.ROOT);
            for (String token : search.split(",")) {
                String wanted = token.trim();
                if (!wanted.isEmpty() && id.contains(wanted)) return true;
            }
            return false;
        }
        return switch (ZenithClient.getConfig().xrayMode) {
            case DIAMOND_DEBRIS -> block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                    || block == Blocks.ANCIENT_DEBRIS;
            case VALUABLE_ORES -> block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                    || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                    || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                    || block == Blocks.NETHER_GOLD_ORE || block == Blocks.ANCIENT_DEBRIS;
            case ORES -> block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
                    || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                    || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
                    || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE
                    || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                    || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                    || block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                    || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                    || block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.ANCIENT_DEBRIS;
        };
    }

    public static boolean isOre(Block block) { return isWhitelisted(block); }
}
