package thaumcraft.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.airborne.AirborneEssentiaManager;
import thaumcraft.research.*;
import thaumcraft.world.aura.AuraManager;
import java.util.*;

/** Server-owned BETA26 infusion stages. Client animation never consumes or creates items/essentia. */
public final class InfusionMatrixBlockEntity extends BlockEntity implements IAspectContainer {
    private boolean active, crafting, scanNeeded = true;
    private float stability, gain, cost = 1;
    private int instability, delay = 5, count, itemCount;
    private List<BlockPos> pedestals = List.of(), problems = List.of();
    private final List<ItemStack> ingredients = new ArrayList<>();
    private ItemStack input = ItemStack.EMPTY, output = ItemStack.EMPTY;
    private String mutationLabel;
    private UUID owner;
    private AspectList needed = new AspectList();
    private float startup;
    private int craftCount;

    public InfusionMatrixBlockEntity(BlockPos pos, BlockState state) { super(InfusionModule.MATRIX.get(), pos, state); }
    public boolean active() { return active; }
    public boolean crafting() { return crafting; }
    public float stability() { return stability; }
    public float gain() { return gain; }
    public float costMultiplier() { return cost; }
    public int cycleDelay() { return delay; }
    public int instability() { return instability; }
    public int remainingItems() { return ingredients.size(); }
    public float startup() { return startup; }
    public int craftCount() { return craftCount; }
    public String stabilityName() { return stability > 12.5F ? "VERY_STABLE" : stability >= 0 ? "STABLE" : stability > -25 ? "UNSTABLE" : "VERY_UNSTABLE"; }
    private float stabilityMod() { return stability > 12.5F ? 5 : stability >= 0 ? 6 : stability > -25 ? 7 : 8; }
    private boolean serverThread() { return level instanceof ServerLevel server && server.getServer().isSameThread(); }
    private void changed() { setChanged(); if (level instanceof ServerLevel server) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    public void rescan() {
        if (!serverThread()) return;
        var surroundings = InfusionStability.scan(level, worldPosition);
        pedestals = surroundings.pedestals(); problems = surroundings.problems(); gain = surroundings.gain(); cost = surroundings.cost(); delay = surroundings.delay(); scanNeeded = false;
    }
    public boolean useCaster(ServerPlayer player) {
        if (!serverThread() || player.isSpectator() || !player.isAlive() || player.level() != level || player.distanceToSqr(worldPosition.getCenter()) > 64) return false;
        if (!active) {
            if (!InfusionStability.valid(level, worldPosition)) return false;
            active = true; rescan(); sound(InfusionModule.START.get(), 1, 1); changed(); return true;
        }
        if (crafting || !InfusionStability.valid(level, worldPosition)) return false;
        rescan(); var central = pedestal(worldPosition.below(2)); if (central == null || central.isEmpty()) return false;
        List<ItemStack> actual = new ArrayList<>();
        for (BlockPos pos : pedestals) { var tile = pedestal(pos); if (tile != null && !tile.isEmpty()) actual.add(tile.getItem(0).copy()); }
        if (actual.isEmpty()) return false;
        var plan = InfusionRecipes.find(player, central.getItem(0), actual); if (plan.isEmpty()) return false;
        var selected = plan.get(); input = selected.input(); output = selected.output(); mutationLabel = selected.mutationLabel();
        ingredients.clear(); ingredients.addAll(selected.components()); needed = new AspectList();
        AspectList raw = selected.aspects();
        for (Aspect aspect : raw.getAspects()) { int amount = (int)(raw.getAmount(aspect) * cost); if (amount > 0) needed.add(aspect, amount); }
        instability = selected.instability(); owner = player.getUUID(); itemCount = 0; crafting = true;
        sound(InfusionModule.START.get(), 1, 1); changed(); return true;
    }
    private InfusionPedestalBlockEntity pedestal(BlockPos pos) { return level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof InfusionPedestalBlockEntity tile ? tile : null; }
    public static void tick(Level level, BlockPos pos, BlockState state, InfusionMatrixBlockEntity tile) {
        if (level.isClientSide) { tile.animate(); return; }
        if (!tile.serverThread()) return;
        tile.count++;
        if (tile.scanNeeded) tile.rescan();
        if (tile.count % (tile.crafting ? 20 : 100) == 0 && !InfusionStability.valid(level, pos)) { tile.active = false; tile.changed(); return; }
        if (tile.active && !tile.crafting && tile.stability < 25 && tile.count % Math.max(5, tile.delay) == 0) {
            tile.stability = Math.min(25, tile.stability + Math.max(.1F, tile.gain)); tile.changed();
        }
        if (tile.active && tile.crafting && tile.count % tile.delay == 0) tile.cycle();
    }
    private void cycle() {
        ServerLevel server = (ServerLevel)level;
        stability = Mth.clamp(stability - server.random.nextFloat() * instability / stabilityMod() + gain, -100, 25);
        var central = pedestal(worldPosition.below(2)); boolean validInput = central != null && InfusionRecipes.sameForCrafting(central.getItem(0), input);
        if (!validInput || stability < 0 && server.random.nextInt(1500) <= Math.abs(stability)) {
            instabilityEvent(server.random.nextInt(24)); stability += 5 + server.random.nextFloat() * 5; recordInstability(); changed();
            if (validInput) return;
        }
        if (!validInput) { abort(); return; }
        if (Arrays.stream(needed.getAspects()).anyMatch(aspect -> needed.getAmount(aspect) > 0)) {
            for (Aspect aspect : needed.getAspects()) {
                int amount = needed.getAmount(aspect); if (amount <= 0) continue;
                var transfer = AirborneEssentiaManager.drain(this, aspect, null, 12, amount > 1 ? delay : 0);
                if (transfer.isPresent()) {
                    needed.reduce(aspect, 1); trail(server, transfer.get().source(), transfer.get().target(), aspect.getColor()); changed(); return;
                }
                stability -= .25F;
            }
            scanNeeded = true; changed(); return;
        }
        if (!ingredients.isEmpty()) {
            for (int i = 0; i < ingredients.size(); i++) {
                for (BlockPos pos : pedestals) {
                    var tile = pedestal(pos);
                    if (tile == null || !InfusionRecipes.sameForCrafting(tile.getItem(0), ingredients.get(i))) continue;
                    if (itemCount == 0) { itemCount = 5; trail(server, pos.above(), worldPosition.below(), 0xAA66FF); }
                    else if (itemCount-- <= 1) {
                        ItemStack remainder = tile.getItem(0).getCraftingRemainingItem();
                        tile.setItemFromInfusion(remainder); ingredients.remove(i);
                    }
                    changed(); return;
                }
                // Original reduce retains zero-valued keys. Missing reagents can request one again.
                Aspect[] paid = needed.getAspects();
                if (paid.length > 0 && server.random.nextInt(1 + i) == 0) { needed.add(paid[server.random.nextInt(paid.length)], 1); stability -= .25F; }
            }
            changed(); return;
        }
        finish(central);
    }
    private void abort() { crafting = false; needed = new AspectList(); instability = 0; ingredients.clear(); output = ItemStack.EMPTY; input = ItemStack.EMPTY; mutationLabel = null; owner = null; itemCount = 0; sound(InfusionModule.FAIL.get(), 1, .6F); changed(); }
    private void finish(InfusionPedestalBlockEntity central) {
        ItemStack current = central.getItem(0), result;
        if (mutationLabel != null) {
            result = current.copy(); Tag value = output.getTag() == null ? null : output.getTag().get(mutationLabel);
            if (value == null) { abort(); return; }
            result.getOrCreateTag().put(mutationLabel, value.copy());
        } else {
            result = output.copy();
            if (current.isDamageableItem() && current.isDamaged() && result.isDamageableItem() && !result.isDamaged())
                result.setDamageValue((int)(result.getMaxDamage() * ((float)current.getDamageValue() / current.getMaxDamage())));
        }
        // Commit the replacement before notifying integrations. A save/reentrant callback sees a completed operation.
        central.setItemFromInfusion(result); crafting = false; needed = new AspectList(); instability = 0;
        output = input = ItemStack.EMPTY; mutationLabel = null; ingredients.clear(); itemCount = 0;
        UUID completedOwner = owner; owner = null; changed();
        ServerLevel server = (ServerLevel)level;
        ServerPlayer player = completedOwner == null ? null : server.getServer().getPlayerList().getPlayer(completedOwner);
        if (player != null) {
            ResearchEvents.recordCraft(player, result);
            net.minecraftforge.event.ForgeEventFactory.firePlayerCraftingEvent(player, result.copy(), new SimpleContainer());
        }
        level.blockEvent(central.getBlockPos(), central.getBlockState().getBlock(), 12, 0); sound(InfusionModule.WAND.get(), .5F, 1);
    }
    private List<LivingEntity> targets() { return level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(10)); }
    private void recordInstability() { for (var entity : targets()) if (entity instanceof ServerPlayer player) KnowledgeStore.recordFact(player, "!INSTABILITY"); }
    private void instabilityEvent(int event) {
        if (event <= 3) eject(0);
        else if (event <= 6) {
            var players = targets().stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).toList();
            if (!players.isEmpty()) { var player = players.get(level.random.nextInt(players.size()));
                if (level.random.nextFloat() < .25F) KnowledgeStore.addNormalWarp(player, 1); else KnowledgeStore.addTemporaryWarp(player, 2 + level.random.nextInt(4)); }
        } else if (event <= 11) zap(event >= 10);
        else if (event <= 13) eject(1);
        else if (event <= 15) eject(2);
        else if (event == 16) eject(3);
        else if (event == 17) eject(4);
        else if (event <= 19 || event == 22) harm(event == 22);
        else if (event <= 21) eject(5);
        else level.explode(null, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 1.5F + level.random.nextFloat(), Level.ExplosionInteraction.BLOCK);
    }
    private void zap(boolean all) {
        for (LivingEntity target : targets()) {
            target.hurt(level.damageSources().magic(), 4 + level.random.nextInt(4));
            trail((ServerLevel)level, worldPosition, target.blockPosition(), 0x660066); if (!all) break;
        }
    }
    private void harm(boolean all) {
        for (LivingEntity target : targets()) { target.addEffect(level.random.nextBoolean() ?
                new MobEffectInstance(InfusionEffects.FLUX_TAINT.get(), 120, 0, false, true) : new MobEffectInstance(InfusionEffects.VIS_EXHAUST.get(), 2400, 0, true, true)); if (!all) break; }
    }
    private void eject(int type) {
        for (int retry = 0; retry < 25 && !pedestals.isEmpty(); retry++) {
            BlockPos pos = pedestals.get(level.random.nextInt(pedestals.size())); var tile = pedestal(pos); if (tile == null || tile.isEmpty()) continue;
            var stabilizer = InfusionInlayBlock.find(level, pos);
            if (stabilizer != null && stabilizer.mitigate(5 + level.random.nextInt(6))) {
                level.blockEvent(pos, tile.getBlockState().getBlock(), 5, 0); trail((ServerLevel)level, worldPosition, pos.above(), 0x660066); trail((ServerLevel)level, pos.above(), stabilizer.getBlockPos(), 0x660066); return;
            }
            ItemStack removed = tile.removeItemNoUpdate(0);
            if (type <= 3 || type == 5) { ItemEntity drop = new ItemEntity(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, removed); drop.setDefaultPickUpDelay(); level.addFreshEntity(drop); }
            if (type == 1 || type == 3) level.setBlockAndUpdate(pos.above(), CatalogBlocks.block("flux_goo").defaultBlockState());
            if (type == 2 || type == 4) AuraManager.addFlux((ServerLevel)level, pos, 5 + level.random.nextInt(5));
            if (type == 5) level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1, Level.ExplosionInteraction.BLOCK);
            level.blockEvent(pos, tile.getBlockState().getBlock(), 11, 0); trail((ServerLevel)level, worldPosition, pos.above(), 0x660066); return;
        }
    }
    private void sound(SoundEvent sound, float volume, float pitch) { level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch); }
    private void localSound(SoundEvent sound) { level.playLocalSound(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), sound, SoundSource.BLOCKS, .5F, 1, false); }
    private void animate() {
        if (active) startup = Math.min(1, startup + Math.max(startup / 10, .001F)); else { startup -= startup / 10; if (startup < .001F) startup = 0; }
        if (crafting) {
            if (craftCount == 0) localSound(InfusionModule.INFUSER_START.get());
            else if (craftCount % 65 == 0) localSound(InfusionModule.LOOP.get());
            craftCount++;
            if (level.random.nextBoolean()) level.addParticle(ParticleTypes.ENCHANT, worldPosition.getX() + .5, worldPosition.getY() - 1, worldPosition.getZ() + .5,
                    level.random.nextGaussian() * .4, level.random.nextDouble() * .4, level.random.nextGaussian() * .4);
        } else craftCount = Math.max(0, Math.min(50, craftCount - 2));
    }
    public static void trail(ServerLevel level, BlockPos from, BlockPos to, int color) {
        var dust = new DustParticleOptions(new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F), .7F);
        for (int i = 0; i <= 12; i++) { double t = i / 12.; var point = from.getCenter().lerp(to.getCenter(), t);
            level.sendParticles(dust, point.x, point.y, point.z, 1, .015, .015, .015, 0); }
    }
    @Override public void setRemoved() { super.setRemoved(); if (level instanceof ServerLevel) AirborneEssentiaManager.forgetConsumer(this); }
    @Override public AspectList getAspects() { return needed.copy(); }
    @Override public void setAspects(AspectList aspects) {}
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect, int amount) { return amount; }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) { return false; }
    @Override public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return false; }
    @Override public boolean doesContainerContain(AspectList aspects) { return false; }
    @Override public int containerContains(Aspect aspect) { return 0; }
    private CompoundTag syncTag() {
        CompoundTag tag = new CompoundTag(); tag.putBoolean("active", active); tag.putBoolean("crafting", crafting); tag.putFloat("stability", stability);
        tag.putFloat("gain", gain); tag.putFloat("cost", cost); tag.putInt("delay", delay); tag.putInt("recipeinst", instability); needed.writeToNBT(tag); return tag;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(syncTag()); tag.putInt("count", count); tag.putInt("itemCount", itemCount);
        tag.put("input", input.save(new CompoundTag())); tag.put("output", output.save(new CompoundTag()));
        if (owner != null) tag.putUUID("owner", owner); if (mutationLabel != null) tag.putString("mutationLabel", mutationLabel);
        ListTag items = new ListTag(); for (ItemStack item : ingredients) items.add(item.save(new CompoundTag())); tag.put("ingredients", items);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); active = tag.getBoolean("active"); crafting = tag.getBoolean("crafting");
        stability = finite(tag.getFloat("stability"), -100, 25); gain = finite(tag.getFloat("gain"), -10000, 10000); cost = finite(tag.getFloat("cost"), .5F, 10);
        delay = Mth.clamp(tag.getInt("delay"), 1, 100); instability = Mth.clamp(tag.getInt("recipeinst"), 0, 10000);
        needed.readFromNBT(tag); for (Aspect aspect : needed.getAspects()) if (needed.getAmount(aspect) < 0) needed.remove(aspect);
        // The small sync tag does not overwrite the server's detached plan when sent to clients.
        if (tag.contains("ingredients", Tag.TAG_LIST)) {
            count = Math.max(0, tag.getInt("count")); itemCount = Mth.clamp(tag.getInt("itemCount"), 0, 5);
            input = ItemStack.of(tag.getCompound("input")); output = ItemStack.of(tag.getCompound("output"));
            mutationLabel = tag.contains("mutationLabel", Tag.TAG_STRING) ? tag.getString("mutationLabel") : null;
            owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null; ingredients.clear();
            for (Tag raw : tag.getList("ingredients", Tag.TAG_COMPOUND)) { ItemStack item = ItemStack.of((CompoundTag)raw); if (!item.isEmpty() && ingredients.size() < 4096) ingredients.add(item.copyWithCount(1)); }
            if (crafting && (input.isEmpty() || output.isEmpty() || owner == null)) { crafting = false; needed = new AspectList(); ingredients.clear(); }
        }
        scanNeeded = true;
    }
    private static float finite(float value, float min, float max) { return Float.isFinite(value) ? Mth.clamp(value, min, max) : min; }
    @Override public CompoundTag getUpdateTag() { return syncTag(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(1.5); }
}
