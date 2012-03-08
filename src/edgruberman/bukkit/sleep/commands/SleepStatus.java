package edgruberman.bukkit.sleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Notification;
import edgruberman.bukkit.sleep.Somnologist;
import edgruberman.bukkit.sleep.State;

class SleepStatus extends Action {

    SleepStatus(final Command owner) {
        super("status", owner);
    }

    @Override
    void execute(final Context context) {
        final World world = this.parseWorld(context);
        if (world == null) {
            Main.messageManager.respond(context.sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return;
        }

        if (!Somnologist.states.containsKey(world)) {
            Main.messageManager.respond(context.sender, "Sleep state for [" + world.getName() + "] is not tracked", MessageLevel.SEVERE, false);
            return;
        }

        final State state = Somnologist.states.get(world);
        final String message = state.notifications.get(Notification.Type.STATUS).format(state.needForSleep(), state.inBed.size(), state.possibleSleepers());
        Main.messageManager.respond(context.sender, message, MessageLevel.STATUS, false);
        if (state.inBed.size() >= 1) state.lull();
    }

    private World parseWorld(final Context context) {
        if (context.sender instanceof Player && context.arguments.size() < 2)
            return ((Player) context.sender).getWorld();

        if (context.arguments.size() < 2) return null;

        return Bukkit.getServer().getWorld(context.arguments.get(1));
    }

}
