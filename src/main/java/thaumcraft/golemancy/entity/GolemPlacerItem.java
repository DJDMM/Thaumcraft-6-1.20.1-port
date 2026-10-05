package thaumcraft.golemancy.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.golemancy.press.GolemDesign;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Original props/xp placer, with server spawn/debit and protection checks. */
public final class GolemPlacerItem extends CatalogItem {
    private static final Set<UUID> PLACING = new HashSet<>();
    public GolemPlacerItem(CatalogModule.Spec spec) { super(spec); }
    @Override public InteractionResult onItemUseFirst(ItemStack held, UseOnContext context) {
        Player player = context.getPlayer(); Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos(), spawn = clicked.relative(context.getClickedFace());
        if (player == null || held.isEmpty() || held.getItem() != this || player.isSpectator()
                || !level.hasChunkAt(clicked) || !level.hasChunkAt(spawn) || !level.getBlockState(clicked).blocksMotion()
                || !player.mayUseItemAt(spawn, context.getClickedFace(), held) || !level.mayInteract(player, spawn)
                || level.isOutsideBuildHeight(spawn) || player.distanceToSqr(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5) > 36) return InteractionResult.FAIL;
        long props = held.hasTag() && held.getTag().contains("props", Tag.TAG_LONG) ? held.getTag().getLong("props") : 0;
        if (GolemDesign.parse(props).isEmpty() || held.hasTag() && held.getTag().contains("props") && !held.getTag().contains("props", Tag.TAG_LONG)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.PASS;
        if (!PLACING.add(player.getUUID())) return InteractionResult.FAIL;
        try {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "golem"));
            if (type == null || !(type.create(level) instanceof ThaumcraftGolemEntity golem)) return InteractionResult.FAIL;
            ItemStack before = held.copy();
            golem.moveTo(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0);
            golem.setOwnerId(player.getUUID()); golem.setValidSpawn(); golem.setProps(props); golem.setHome(spawn);
            golem.setRankXp(held.hasTag() ? held.getTag().getInt("xp") : 0);
            if (!level.noCollision(golem, golem.getBoundingBox()) || !level.addFreshEntity(golem)) return InteractionResult.FAIL;
            // Forge EntityJoinLevelEvent can change the held stack or remove the candidate.
            if (!golem.isAlive() || golem.isRemoved() || !ItemStack.matches(before, player.getItemInHand(context.getHand()))) {
                golem.discard(); return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
            return InteractionResult.CONSUME;
        } finally { PLACING.remove(player.getUUID()); }
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
        long props = stack.hasTag() && stack.getTag().contains("props", Tag.TAG_LONG) ? stack.getTag().getLong("props") : 0;
        GolemDesign.parse(props).ifPresent(design -> {
            if (design.hasTrait(GolemDesign.Trait.SMART)) {
                Component rank = Component.translatable("golem.rank").append(" " + design.rank());
                if (design.rank() < 10) rank = rank.copy().append(" (" + Math.max(0, stack.hasTag() ? stack.getTag().getInt("xp") : 0)
                        + "/" + ((design.rank() + 1) * (design.rank() + 1) * 1000) + ")");
                lines.add(rank.copy().withStyle(ChatFormatting.GOLD));
            }
            lines.add(Component.translatable(design.material().nameKey()).withStyle(ChatFormatting.GREEN));
            for (GolemDesign.Trait trait : design.traits()) lines.add(Component.literal("- ").append(Component.translatable(trait.nameKey())).withStyle(ChatFormatting.BLUE));
        });
    }
}
