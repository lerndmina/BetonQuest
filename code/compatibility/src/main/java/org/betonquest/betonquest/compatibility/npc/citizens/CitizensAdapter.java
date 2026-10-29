package org.betonquest.betonquest.compatibility.npc.citizens;

import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.PlayerFilter;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.quest.npc.Npc;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

/**
 * Citizens Compatibility Adapter for BetonQuest Npcs.
 * <p>
 * Work on a spawned NPC runs on the thread owning its entity, spawning on the thread owning the target location.
 * That keeps all changes to one NPC on one thread at a time on Folia; on Paper it is all the main thread.
 */
public class CitizensAdapter implements Npc<NPC> {

    /**
     * The Citizens NPC instance.
     */
    private final NPC npc;

    /**
     * Create a new Citizens Npc Adapter.
     *
     * @param npc the Citizens NPC instance
     */
    public CitizensAdapter(final NPC npc) {
        this.npc = npc;
    }

    @Override
    public NPC getOriginal() {
        return npc;
    }

    @Override
    public String getName() {
        return npc.getName();
    }

    @Override
    public String getFormattedName() {
        return npc.getFullName();
    }

    @Override
    public Optional<Location> getLocation() {
        final Entity entity = npc.getEntity();
        if (entity == null) {
            return Optional.ofNullable(npc.getStoredLocation());
        }
        return Optional.of(entity.getLocation());
    }

    @Override
    public Optional<Location> getEyeLocation() {
        final Entity entity = npc.getEntity();
        if (entity instanceof LivingEntity) {
            return Optional.of(((LivingEntity) entity).getEyeLocation());
        }
        return getLocation();
    }

    @Override
    public void teleport(final Location location) {
        CitizensIntegrator.getCitizensMoveInstance().stopNPCMoving(npc);
        onNpcThread(() -> {
            npc.getNavigator().cancelNavigation();
            if (npc.isSpawned()) {
                npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        });
    }

    @Override
    public boolean isSpawned() {
        return npc.isSpawned();
    }

    @Override
    public void spawn(final Location location) {
        if (!npc.isSpawned()) {
            Scheduling.executeAtLocation(BetonQuest.getInstance(), location, () -> {
                if (!npc.isSpawned()) {
                    npc.spawn(location, SpawnReason.PLUGIN);
                }
            });
        }
    }

    @Override
    public void despawn() {
        if (npc.isSpawned()) {
            npc.despawn();
        }
    }

    @Override
    public void show(final OnlineProfile onlineProfile) {
        onNpcThread(() -> {
            final PlayerFilter filter = npc.getOrAddTrait(PlayerFilter.class);
            if (filter.isAllowlist()) {
                filter.addPlayer(onlineProfile.getPlayerUUID());
            } else {
                filter.removePlayer(onlineProfile.getPlayerUUID());
            }
        });
    }

    @Override
    public void hide(final OnlineProfile onlineProfile) {
        onNpcThread(() -> {
            final PlayerFilter filter = npc.getOrAddTrait(PlayerFilter.class);
            if (filter.isDenylist()) {
                filter.addPlayer(onlineProfile.getPlayerUUID());
            } else {
                filter.removePlayer(onlineProfile.getPlayerUUID());
            }
        });
    }

    /**
     * Runs the task now if the current thread owns the NPC entity, otherwise on the thread owning it.
     * An NPC without entity, or one despawned before the task ran, is handled on the global region.
     *
     * @param task the task
     */
    private void onNpcThread(final Runnable task) {
        final Entity entity = npc.getEntity();
        if (entity == null) {
            Scheduling.executeGlobal(BetonQuest.getInstance(), task);
        } else {
            Scheduling.executeForEntityOrGlobal(BetonQuest.getInstance(), entity, task);
        }
    }
}
