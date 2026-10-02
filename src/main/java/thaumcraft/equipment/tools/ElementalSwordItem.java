package thaumcraft.equipment.tools;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.Field;

/** BETA26 sustained wind lift and repulsion; server motion and durability are authoritative. */
public final class ElementalSwordItem extends ToolItems.Sword {
    private static final Field FLOAT_TICKS = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9737_");
    public ElementalSwordItem() { super(ToolMaterials.ELEMENTAL, 0); }
    @Override public Rarity getRarity(ItemStack stack) { return Rarity.RARE; }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand); return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 movement = user.getDeltaMovement();
        double y = movement.y;
        if (y < 0) { y /= 1.2000000476837158; user.fallDistance /= 1.2F; }
        y += .07999999821186066;
        if (y > .5) y = .20000000298023224;
        user.setDeltaMovement(movement.x, y, movement.z);
        user.hasImpulse = true;
        if (user instanceof ServerPlayer player && player.connection != null) {
            try { FLOAT_TICKS.setInt(player.connection, 0); }
            catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot reset elemental sword floating counter", exception); }
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(2.5),
                entity -> entity.isAlive() && entity != user && !(entity instanceof Player) && entity != user.getVehicle())) {
            Vec3 offset = target.position().subtract(user.position());
            target.push(offset.x / (2.5 * (offset.length() + .1)), offset.y / (2.5 * (offset.length() + .1)),
                    offset.z / (2.5 * (offset.length() + .1)));
            target.hurtMarked = true;
        }
        int age = getUseDuration(stack) - remaining;
        if (age % 20 == 0) {
            ToolSupport.wind(user, .5F);
            ToolSupport.damage(stack, user, user.getUsedItemHand(), 1);
        }
        server.sendParticles(ParticleTypes.SMOKE, user.getX(), user.getY() + .1, user.getZ(), 5, .5, .05, .5, .015);
    }
}
