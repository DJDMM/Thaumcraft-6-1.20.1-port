package thaumcraft.equipment.tools;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class ToolEvents {
    private static final Map<UUID, Following> FOLLOWING = new HashMap<>();
    private ToolEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakApproved(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && ToolMining.isTool(player.getMainHandItem()))
            ToolMining.approve(player, event.getPos(), event.getState(), event.getExpToDrop());
    }
    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide) ToolMining.face(event.getEntity(), event.getFace());
    }
    @SubscribeEvent public static void spawned(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemEntity item) ToolMining.spawnedDrop(item);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void arcing(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getTarget().isAlive()
                || !ToolMining.isTool(player.getMainHandItem())) return;
        int rank = ToolSupport.enchantment(player.getMainHandItem(), EnumInfusionEnchantment.ARCING);
        if (rank == 0) return;
        int count = 0;
        for (Entity target : player.level().getEntities(player, event.getTarget().getBoundingBox().inflate(1.5 + rank, 1 + rank / 2F, 1.5 + rank))) {
            if (!(target instanceof Mob mob) || !target.isAlive() || target == event.getTarget() || friendly(player, target)) continue;
            // TC6 calls the inherited attackEntityAsMob first; Player's ordinary attack method is not called recursively.
            player.doHurtTarget(target);
            if (!target.hurt(player.damageSources().playerAttack(player), (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .5F)) continue;
            EnchantmentHelper.doPostHurtEffects(mob, player);
            double yaw = Math.toRadians(player.getYRot());
            target.push(-Math.sin(yaw) * .5, .1, Math.cos(yaw) * .5);
            target.hurtMarked = true;
            ((ServerLevel)player.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() / 2,
                    target.getZ(), 1, 0, 0, 0, 0);
            if (++count >= rank) break;
        }
        if (count > 0) ToolSupport.wind(player, 1F);
    }
    private static boolean friendly(Player player, Entity target) {
        return target == player || player.isPassengerOfSameVehicle(target) || player.isAlliedTo(target)
                || target instanceof OwnableEntity ownable && player.equals(ownable.getOwner());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void livingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !ToolMining.isTool(player.getMainHandItem())
                || ToolSupport.enchantment(player.getMainHandItem(), EnumInfusionEnchantment.COLLECTOR) == 0) return;
        for (ItemEntity drop : event.getDrops()) follow(drop, player);
    }

    /** type=10 in TC6 means blue collector FX, not a ten-tick delay. The original homing age starts at 20. */
    static void follow(ItemEntity drop, ServerPlayer owner) {
        FOLLOWING.put(drop.getUUID(), new Following(drop, owner.getUUID(), drop.noPhysics, drop.isNoGravity()));
        drop.noPhysics = true;
        drop.setNoGravity(true);
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ToolMining.clearApprovals();
        Iterator<Following> iterator = FOLLOWING.values().iterator();
        while (iterator.hasNext()) {
            Following following = iterator.next();
            ItemEntity drop = following.drop;
            ServerPlayer owner = drop.getServer() == null ? null : drop.getServer().getPlayerList().getPlayer(following.owner);
            if (!drop.isAlive() || owner == null || !owner.isAlive() || owner.level() != drop.level() || ++following.ticks > 1200) {
                following.restore(); iterator.remove(); continue;
            }
            Vec3 offset = owner.position().add(0, owner.getBbHeight() / 2, 0).subtract(drop.position());
            if (following.age > 1) following.age--;
            if (offset.length() > .5) drop.setDeltaMovement(offset.scale(1 / (offset.length() * following.age)));
            else { drop.setDeltaMovement(drop.getDeltaMovement().scale(.10000000149)); following.restore(); iterator.remove(); }
            drop.hurtMarked = true;
            if (following.ticks % 2 == 0 && drop.level() instanceof ServerLevel level)
                level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(.33F, .33F, 1F), .5F),
                        drop.getX(), drop.getY() + .15, drop.getZ(), 1, .03, .03, .03, 0);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ToolMining.logout(event.getEntity());
        FOLLOWING.values().removeIf(following -> {
            if (!following.owner.equals(event.getEntity().getUUID())) return false;
            following.restore(); return true;
        });
    }
    private static final class Following {
        final ItemEntity drop; final UUID owner; final boolean noPhysics, noGravity;
        int age = 20, ticks;
        Following(ItemEntity drop, UUID owner, boolean noPhysics, boolean noGravity) {
            this.drop = drop; this.owner = owner; this.noPhysics = noPhysics; this.noGravity = noGravity;
        }
        void restore() { drop.noPhysics = noPhysics; drop.setNoGravity(noGravity); }
    }
}
