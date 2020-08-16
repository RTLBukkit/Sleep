package edgruberman.bukkit.sleep.util;

import net.minecraft.server.v1_16_R2.EntityPlayer;
import net.minecraft.server.v1_16_R2.BlockPosition;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;


public class BedLocation {
    public static Location get(Player player) {
        final EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        CraftWorld world = nmsPlayer.server.getWorldServer(nmsPlayer.getSpawnDimension()).getWorld();
        final BlockPosition blockPosition = nmsPlayer.getSpawn();
        if(blockPosition == null) return null;
        return new Location(world, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    }
}
