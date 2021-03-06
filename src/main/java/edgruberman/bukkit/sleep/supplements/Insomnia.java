package edgruberman.bukkit.sleep.supplements;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.Supplement;
import edgruberman.bukkit.sleep.Reason;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.events.SleepNotify;
import edgruberman.bukkit.sleep.events.SleepStatus;

/** prevents deep sleep from occurring which would cause Minecraft to change the time to morning */
public final class Insomnia extends Supplement {

    // TODO research if -10 is unnecessary to compensate for any background processing before task starts counting
    /** ticks in bed at which Minecraft declares deep sleep which causes morning  */
    private static final long BEFORE_DEEP_SLEEP_TICKS = 100 - 10;

    public Insomnia(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;
        new DelayedLoudNoise(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepNotify(final SleepNotify notify) {
        if (!notify.getWorld().equals(this.state.world)) return;
        if (notify.getReason() != Reason.ENTER && notify.getReason() != Reason.LEAVE) return;
        notify.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepStatus(final SleepStatus request) {
        if (!request.getWorld().equals(this.state.world)) return;
        request.setCancelled(true);
        this.state.courier.send(request.getRequestor(), "insomnia.status", this.state.world.getName());
    }



    private final class DelayedLoudNoise implements Runnable, Listener {

        private final Player player;
        private final int taskId;

        private DelayedLoudNoise(final Player player) {
            this.player = player;

            this.taskId = Bukkit.getScheduler().runTaskLater(Insomnia.this.implementor, this, Insomnia.BEFORE_DEEP_SLEEP_TICKS).getTaskId();
            if (this.taskId == -1) {
                Insomnia.this.implementor.getLogger().log(Level.WARNING, "Failed to schedule insomnia task for {0}", this.player.getName());
                return;
            }

            Bukkit.getPluginManager().registerEvents(this, Insomnia.this.implementor);
        }

        @Override
        public void run() {
            if (!this.player.isSleeping()) {
                Insomnia.this.implementor.getLogger().log(Level.FINEST, "Insomnia cancelled for {0}; No longer in bed", this.player.getName());
                return;
            }

            Insomnia.this.implementor.getLogger().log(Level.FINEST, "Insomnia sets in for {0}; Setting spawn point then ejecting from bed...", this.player.getName());

            this.player.wakeup(true);

            Insomnia.this.state.courier.send(this.player, "insomnia.eject");
        }

        @EventHandler
        private void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
            if (!leave.getPlayer().equals(this.player)) return;
            Bukkit.getScheduler().cancelTask(this.taskId);
        }

    }

}
