package org.betonquest.betonquest.compatibility.mythicmobs.npc;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.quest.npc.Npc;
import org.betonquest.betonquest.compatibility.mythicmobs.MythicHider;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.Optional;

/**
 * MythicMobs {@link Npc} Adapter.
 *
 * @param activeMob   The ActiveMob instance.
 * @param mythicHider the hider for mobs
 */
public record MythicMobsNpcAdapter(ActiveMob activeMob, MythicHider mythicHider) implements Npc<ActiveMob> {

    @Override
    public ActiveMob getOriginal() {
        return activeMob;
    }

    @Override
    public String getName() {
        return activeMob.getName();
    }

    @Override
    public String getFormattedName() {
        return Objects.requireNonNullElse(activeMob.getDisplayName(), getName());
    }

    @Override
    public Optional<Location> getLocation() {
        if (!isSpawned()) {
            return Optional.empty();
        }
        return Optional.of(activeMob.getEntity().getBukkitEntity().getLocation());
    }

    @Override
    public Optional<Location> getEyeLocation() {
        if (!isSpawned()) {
            return Optional.empty();
        }
        final Entity entity = activeMob.getEntity().getBukkitEntity();
        if (entity instanceof LivingEntity) {
            return Optional.of(((LivingEntity) entity).getEyeLocation());
        }
        return getLocation();
    }

    @Override
    public void teleport(final Location location) {
        Scheduling.teleport(BetonQuest.getInstance(), activeMob.getEntity().getBukkitEntity(), location);
    }

    @Override
    public boolean isSpawned() {
        return activeMob.getEntity().isValid();
    }

    @Override
    public void spawn(final Location location) {
        // Already existent
    }

    @Override
    public void despawn() {
        activeMob.despawn();
    }

    @Override
    public void show(final OnlineProfile onlineProfile) {
        mythicHider.show(onlineProfile, activeMob.getEntity().getBukkitEntity());
    }

    @Override
    public void hide(final OnlineProfile onlineProfile) {
        mythicHider.hide(onlineProfile, activeMob.getEntity().getBukkitEntity());
    }
}
