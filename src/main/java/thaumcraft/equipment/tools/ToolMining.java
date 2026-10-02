package thaumcraft.equipment.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Forge 1.20 removed HarvestDropsEvent. A bounded harvest context retains Block.playerDestroy and the real loot
 * pipeline, transforming only freshly spawned ItemEntities. Tile-entity blocks use the ordinary vanilla path.
 */
public final class ToolMining {
    private static final Map<UUID, Approval> APPROVALS = new HashMap<>();
    private static final Map<UUID, Direction> FACES = new HashMap<>();
    private static final ThreadLocal<Harvest> HARVEST = new ThreadLocal<>();
    private ToolMining() {}

    static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof ToolItems.Pick || stack.getItem() instanceof ToolItems.Shovel
                || stack.getItem() instanceof ToolItems.Axe || stack.getItem() instanceof ToolItems.Sword
                || stack.getItem() instanceof ToolItems.Hoe;
    }
    static void approve(ServerPlayer player, BlockPos pos, BlockState state, int experience) {
        APPROVALS.put(player.getUUID(), new Approval(player.serverLevel(), pos.immutable(), state,
                player.serverLevel().getGameTime(), experience));
    }
    static void face(Player player, Direction face) { if (face != null) FACES.put(player.getUUID(), face); }
    static void clearApprovals() { APPROVALS.clear(); }
    static void logout(Player player) { APPROVALS.remove(player.getUUID()); FACES.remove(player.getUUID()); }

    /** Returns true only when this class replaces the one ordinary block-break action. */
    public static boolean start(ItemStack stack, BlockPos origin, Player user) {
        if (!(user instanceof ServerPlayer player) || HARVEST.get() != null || player.isCreative() || stack.isEmpty()) return false;
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(origin);
        if (!effective(stack, state) || state.hasBlockEntity() || !state.canHarvestBlock(level, origin, player)) return false;
        boolean burrow = !player.isShiftKeyDown() && ToolSupport.enchantment(stack, EnumInfusionEnchantment.BURROWING) > 0
                && (state.is(BlockTags.LOGS) || isOre(state));
        boolean destructive = !player.isShiftKeyDown() && ToolSupport.enchantment(stack, EnumInfusionEnchantment.DESTRUCTIVE) > 0;
        boolean refining = ToolSupport.enchantment(stack, EnumInfusionEnchantment.REFINING) > 0;
        boolean collector = !player.isShiftKeyDown() && ToolSupport.enchantment(stack, EnumInfusionEnchantment.COLLECTOR) > 0;
        if (!burrow && !destructive && !refining && !collector) return false;
        if (burrow) {
            BlockPos furthest = furthest(level, origin, state, state.is(BlockTags.LOGS) ? 2 : 1);
            if (harvest(player, stack, furthest, furthest.equals(origin))) {
                for (BlockPos leaf : BlockPos.betweenClosed(furthest.offset(-3, -3, -3), furthest.offset(3, 3, 3))) {
                    BlockState leaves = level.getBlockState(leaf);
                    if (leaves.is(BlockTags.LEAVES)) level.scheduleTick(leaf.immutable(), leaves.getBlock(), 50 + player.getRandom().nextInt(75));
                }
            }
            return true;
        }
        if (!harvest(player, stack, origin, true)) return true;
        if (destructive) {
            Direction face = FACES.getOrDefault(player.getUUID(), Direction.getNearest(
                    (float)-player.getLookAngle().x, (float)-player.getLookAngle().y, (float)-player.getLookAngle().z));
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0 || stack.isEmpty()) continue;
                BlockPos pos = face.getAxis() == Direction.Axis.Y ? origin.offset(a, 0, b)
                        : face.getAxis() == Direction.Axis.Z ? origin.offset(a, b, 0) : origin.offset(0, b, a);
                if (effective(stack, level.getBlockState(pos))) harvest(player, stack, pos, false);
            }
        }
        return true;
    }

    private static boolean harvest(ServerPlayer player, ItemStack tool, BlockPos pos, boolean approvedCentral) {
        ServerLevel level = player.serverLevel();
        if (tool.isEmpty() || player.isSpectator() || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.mayInteract(player, pos) || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0
                || state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()
                || !state.canHarvestBlock(level, pos, player)) return false;
        int experience;
        Approval approval = approvedCentral ? APPROVALS.remove(player.getUUID()) : null;
        if (approval != null && approval.level == level && approval.tick == level.getGameTime()
                && approval.pos.equals(pos) && approval.state == state) experience = approval.experience;
        else experience = ForgeHooks.onBlockBreakEvent(level, player.gameMode.getGameModeForPlayer(), player, pos);
        if (experience < 0 || level.getBlockState(pos) != state) return false;
        ItemStack before = tool.copy();
        Harvest context = new Harvest(player, pos.immutable(), before);
        HARVEST.set(context);
        try {
            if (!state.onDestroyedByPlayer(level, pos, player, true, level.getFluidState(pos))) return false;
            state.getBlock().destroy(level, pos, state);
            // Use original vanilla/Forge loot generation and block overrides; no independent synthetic loot table.
            state.getBlock().playerDestroy(level, player, pos, state, null, before);
            if (approvedCentral) tool.mineBlock(level, state, pos, player);
            else ToolSupport.damage(tool, player, InteractionHand.MAIN_HAND, 1);
            if (tool.isEmpty()) ForgeEventFactory.onPlayerDestroyItem(player, before, InteractionHand.MAIN_HAND);
            if (experience > 0) state.getBlock().popExperience(level, pos, experience);
            level.levelEvent(null, 2001, pos, Block.getId(state));
            if (context.refined) level.playSound(null, pos, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, .2F, .7F + player.getRandom().nextFloat() * .2F);
            return true;
        } finally { HARVEST.remove(); }
    }

    /** Called before an ItemEntity is added; it can change its contents without losing vanilla loot modifiers. */
    static void spawnedDrop(ItemEntity drop) {
        Harvest context = HARVEST.get();
        if (context == null || drop.level() != context.player.level() || drop.distanceToSqr(
                context.pos.getX() + .5, context.pos.getY() + .5, context.pos.getZ() + .5) > 16) return;
        int refining = ToolSupport.enchantment(context.tool, EnumInfusionEnchantment.REFINING);
        if (refining > 0) {
            ItemStack refined = refine(drop.getItem(), context.player, (1 + refining) * .125F);
            if (refined != drop.getItem()) { drop.setItem(refined); context.refined = true; }
        }
        if (!context.player.isShiftKeyDown() && ToolSupport.enchantment(context.tool, EnumInfusionEnchantment.COLLECTOR) > 0)
            ToolEvents.follow(drop, context.player);
    }

    private static ItemStack refine(ItemStack input, ServerPlayer player, float chance) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(input.getItem());
        if (id == null) return input;
        String cluster = switch (id.toString()) {
            case "minecraft:raw_iron", "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> "iron";
            case "minecraft:raw_gold", "minecraft:gold_ore", "minecraft:deepslate_gold_ore" -> "gold";
            case "minecraft:raw_copper", "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> "copper";
            case "minecraft:quartz" -> "quartz";
            case "thaumcraft:ore_cinnabar" -> "cinnabar";
            default -> null;
        };
        // TC6 also installs these mappings through oreDictionary; modern optional ores use Forge item tags.
        if (cluster == null) for (String metal : new String[]{"tin", "silver", "lead", "copper", "iron", "gold"}) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("forge", "ores/" + metal));
            if (input.is(tag)) { cluster = metal; break; }
        }
        if (cluster == null || player.getRandom().nextFloat() > chance) return input;
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "cluster_" + cluster));
        return item == null || item == net.minecraft.world.item.Items.AIR ? input : new ItemStack(item, input.getCount());
    }

    private static boolean effective(ItemStack tool, BlockState state) {
        return !state.isAir() && (tool.getDestroySpeed(state) > 1F || state.requiresCorrectToolForDrops() && tool.isCorrectToolForDrops(state));
    }
    public static boolean isOre(BlockState state) {
        return state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("forge", "ores")));
    }
    static boolean isTaint(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("thaumcraft") && id.getPath().startsWith("taint_");
    }

    /** The original greedy recursion, with a finite depth and loaded-chunk guard instead of global mutable state. */
    private static BlockPos furthest(ServerLevel level, BlockPos origin, BlockState original, int reach) {
        BlockPos current = origin;
        double distance = 0;
        for (int step = 0; step < 4096; step++) {
            BlockPos next = null;
            search: for (int x = -reach; x <= reach; x++) for (int y = reach; y >= -reach; y--) for (int z = -reach; z <= reach; z++) {
                BlockPos candidate = current.offset(x, y, z);
                if (Math.abs(candidate.getX() - origin.getX()) > 24 || Math.abs(candidate.getY() - origin.getY()) > 48
                        || Math.abs(candidate.getZ() - origin.getZ()) > 24) return current;
                if (!level.hasChunkAt(candidate)) continue;
                BlockState state = level.getBlockState(candidate);
                // Original compares the block and damageDropped (log axis is not a separate dropped item).
                if (state.getBlock() == original.getBlock() && state.getDestroySpeed(level, candidate) >= 0
                        && candidate.distSqr(origin) > distance) { next = candidate; distance = candidate.distSqr(origin); break search; }
            }
            if (next == null) break;
            current = next;
        }
        return current;
    }

    private record Approval(ServerLevel level, BlockPos pos, BlockState state, long tick, int experience) {}
    private static final class Harvest {
        final ServerPlayer player; final BlockPos pos; final ItemStack tool; boolean refined;
        Harvest(ServerPlayer player, BlockPos pos, ItemStack tool) { this.player = player; this.pos = pos; this.tool = tool; }
    }
}
