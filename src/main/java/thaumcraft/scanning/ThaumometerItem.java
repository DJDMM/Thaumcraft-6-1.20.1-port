package thaumcraft.scanning;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.AuromancyProgressionEvents;

import javax.annotation.Nullable;
import java.util.List;

public final class ThaumometerItem extends Item {
    public static final double SCAN_RANGE = 9.0;

    public ThaumometerItem() { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        scan(player, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null) scan(context.getPlayer(), context.getHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        scan(player, hand);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.thaumcraft.thaumometer").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.thaumcraft.thaumometer.offhand").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.thaumcraft.thaumometer.aura").withStyle(ChatFormatting.GRAY));
    }

    public void scan(Player player, InteractionHand hand) {
        if (player.level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> thaumcraft.scanning.client.ThaumometerClient.onUse(player, hand));
            return;
        }
        if (!(player instanceof ServerPlayer server) || player.isSpectator() || !player.isAlive()
                || player.getItemInHand(hand).getItem() != this || !server.serverLevel().getServer().isSameThread()) return;
        ScanTarget target = findTarget(server, hand);
        if (target == null && thaumcraft.research.celestial.CelestialScanner.scan(server)
                != thaumcraft.research.celestial.CelestialScanner.Result.NOT_APPLICABLE) return;
        Object scanned = target == null ? null : scannedObject(server, hand, target);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.thaumcraft.scan.nothing"), true);
            return;
        }
        boolean aspectDiscovered = target.aspects.size() > 0 && KnowledgeStore.recordScan(server, target.key, target.aspects);
        // Original ScanEntity/ScanItem facts are independent of aspect discovery and have no lesson-stage gate.
        boolean factDiscovered = AuromancyProgressionEvents.recordScannedFact(server, scanned);
        var contents = target.location.kind == TargetKind.BLOCK
                ? ThaumometerScanning.scanContents(server, target.location.blockPos)
                : ThaumometerScanning.ContentsResult.EMPTY;
        boolean discovered = aspectDiscovered || factDiscovered || contents.changed();
        player.displayClientMessage(Component.translatable(discovered ? "tc.knownobject" : "tc.unknownobject")
                .withStyle(ChatFormatting.ITALIC, discovered ? ChatFormatting.GREEN : ChatFormatting.DARK_PURPLE), true);
        // TC6 presents aspects beside the object, rather than a long action-bar list.
        ScanningNetwork.sendScan(server, target, discovered);
        if (contents.capped()) player.displayClientMessage(Component.translatable("tc.invtoolarge")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE), true);
    }

    /** Reconstruct the target from server-owned inventory, position and look direction. */
    @Nullable
    static ScanTarget findTarget(ServerPlayer player, InteractionHand hand) {
        TargetLocation location = locateTarget(player, hand);
        if (location == null) return null;
        if (location.kind == TargetKind.HELD_ITEM) {
            var target = itemTarget(player.getItemInHand(otherHand(hand)), location.position);
            return new ScanTarget(target.key, target.name, target.aspects, target.position, location);
        }
        if (location.kind == TargetKind.ENTITY) {
            Entity selected = player.level().getEntity(location.entityId);
            if (selected == null) return null;
            if (selected instanceof ItemEntity item) {
                var target = itemTarget(item.getItem(), location.position);
                return new ScanTarget(target.key, target.name, target.aspects, target.position, location);
            }
            AspectList aspects = AspectRegistry.getAspects(selected);
            return new ScanTarget(scanKey("entity:" + ForgeRegistries.ENTITY_TYPES.getKey(selected.getType()), aspects),
                    selected.getName(), aspects, location.position, location);
        }
        var state = player.level().getBlockState(location.blockPos);
        var stack = blockScanStack(player, location);
        AspectList aspects = stack.isEmpty() ? AspectRegistry.getAspects(state) : AspectRegistry.getAspects(stack);
        String key = stack.isEmpty() ? "block:" + ForgeRegistries.BLOCKS.getKey(state.getBlock())
                : "item:" + ForgeRegistries.ITEMS.getKey(stack.getItem());
        return new ScanTarget(scanKey(key, aspects), state.getBlock().getName(), aspects, location.position, location);
    }

    /** Original getItemFromParms resolves the native pick stack, including liquid buckets. */
    static ItemStack blockScanStack(Player player, TargetLocation location) {
        var state = player.level().getBlockState(location.blockPos);
        ItemStack stack = state.getBlock().getCloneItemStack(state,
                new BlockHitResult(location.position, location.face, location.blockPos, false),
                player.level(), location.blockPos, player);
        if (stack.isEmpty()) {
            if (state.is(net.minecraft.world.level.block.Blocks.WATER)) return new ItemStack(Items.WATER_BUCKET);
            if (state.is(net.minecraft.world.level.block.Blocks.LAVA)) return new ItemStack(Items.LAVA_BUCKET);
        }
        return stack;
    }

    /** Actual server object behind a reconstructed target; never supplied by a client discovery request. */
    @Nullable static Object scannedObject(ServerPlayer player, InteractionHand hand, ScanTarget target) {
        return switch (target.location.kind) {
            case HELD_ITEM -> player.getItemInHand(otherHand(hand));
            case ENTITY -> player.level().getEntity(target.location.entityId);
            case BLOCK -> player.level().getBlockState(target.location.blockPos);
        };
    }

    /** Geometry only: safe on either side, with no aspect or knowledge lookup. */
    @Nullable
    public static TargetLocation locateTarget(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            ItemStack held = player.getItemInHand(otherHand(hand));
            if (!held.isEmpty() && !(held.getItem() instanceof ThaumometerItem))
                return new TargetLocation(TargetKind.HELD_ITEM, -1, null, Direction.UP, player.getEyePosition());
        }
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(SCAN_RANGE));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        double nearest = block.getType() == HitResult.Type.MISS ? SCAN_RANGE * SCAN_RANGE : start.distanceToSqr(block.getLocation());
        Entity selected = null;
        Vec3 selectedPoint = null;
        for (Entity candidate : player.level().getEntities(player, new AABB(start, end).inflate(1.0),
                entity -> !entity.isRemoved() && !entity.isSpectator() && (entity.isPickable() || entity instanceof ItemEntity
                        || AuromancyProgressionEvents.scanFact(entity) != null))) {
            var intersection = candidate.getBoundingBox().inflate(0.1).clip(start, end);
            if (intersection.isEmpty()) continue;
            double distance = start.distanceToSqr(intersection.get());
            if (distance < nearest) { nearest = distance; selected = candidate; selectedPoint = intersection.get(); }
        }
        if (selected != null) {
            return new TargetLocation(TargetKind.ENTITY, selected.getId(), null, Direction.UP, selectedPoint);
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            return new TargetLocation(TargetKind.BLOCK, -1, block.getBlockPos().immutable(), block.getDirection(), block.getLocation());
        }
        return null;
    }

    @Nullable
    public static InteractionHand heldHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof ThaumometerItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof ThaumometerItem) return InteractionHand.OFF_HAND;
        return null;
    }

    /** TC6 Vis Resonator shows the aura meter without scanning objects or awarding knowledge. */
    @Nullable public static InteractionHand auraHand(Player player) {
        var scanner = heldHand(player); if (scanner != null) return scanner;
        for (InteractionHand hand : InteractionHand.values())
            if (thaumcraft.auromancy.FocusSelection.isCaster(player.getItemInHand(hand))
                    || ForgeRegistries.ITEMS.getKey(player.getItemInHand(hand).getItem()).equals(thaumcraft.infusion.InfusionModule.id("vis_resonator"))) return hand;
        return null;
    }

    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    static ScanTarget itemTarget(ItemStack stack, Vec3 position) {
        AspectList aspects = AspectRegistry.getAspects(stack);
        return new ScanTarget(scanKey("item:" + ForgeRegistries.ITEMS.getKey(stack.getItem()), aspects), stack.getHoverName(), aspects, position);
    }

    // BETA26 ScanGeneric keys only the type (plus old non-damageable metadata,
    // represented by separate modern IDs). Effects and enchantments have their own facts.
    static String scanKey(String identity, AspectList aspects) {
        return thaumcraft.research.PlayerKnowledge.scanIdentity(identity);
    }

    public enum TargetKind { BLOCK, ENTITY, HELD_ITEM }
    public record TargetLocation(TargetKind kind, int entityId, @Nullable BlockPos blockPos, Direction face, Vec3 position) {}
    record ScanTarget(String key, Component name, AspectList aspects, Vec3 position, TargetLocation location) {
        ScanTarget(String key, Component name, AspectList aspects, Vec3 position) {
            this(key, name, aspects, position, new TargetLocation(TargetKind.HELD_ITEM, -1, null, Direction.UP, position));
        }
    }
}
