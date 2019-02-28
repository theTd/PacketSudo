package org.totemcraft.packetsudo;

import com.sainttx.auctions.util.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PacketSudoPlugin extends JavaPlugin {

    private static PacketSudoPlugin instance;

    public static PacketSudoPlugin getInstance() {
        return instance;
    }


    private Field fieldHumanEntity_perm;
    private Field fieldCraftEntity_entity;
    private Field fieldEntityPlayer_playerConnection;

    @Override
    public void onEnable() {
        instance = this;

        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length <= 1) return false;
                String playerName = args[0];
                Player player = Bukkit.getPlayerExact(playerName);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "没有找到叫做 \"" + playerName + "\" 的玩家");
                    return true;
                }
                String cmd = arrangeCmd(args, 1);
                boolean force = command.getName().equals("packetsudoforce");
                boolean op = command.getName().equals("packetsudoop");
                executeCommand(player, cmd, force, op);
                return true;
            }
        };

        getCommand("packetsudo").setExecutor(executor);
        getCommand("packetsudoforce").setExecutor(executor);
        getCommand("packetsudoop").setExecutor(executor);
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public void executeCommand(Player player, String command, boolean force, boolean op) {
        try {
            // region prepare
            PermissibleBase backup = null;
            boolean restoreOp = false;
            if (force) {
                if (fieldHumanEntity_perm == null) {
                    Class humanEntityClass = ReflectionUtil.getOBCClass("entity.CraftHumanEntity");
                    fieldHumanEntity_perm = humanEntityClass.getDeclaredField("perm");
                    fieldHumanEntity_perm.setAccessible(true);
                }
                backup = (PermissibleBase) fieldHumanEntity_perm.get(player);
                fieldHumanEntity_perm.set(player, new PermissibleBase(player) {
                    @Override
                    public boolean hasPermission(String inName) {
                        return true;
                    }

                    @Override
                    public boolean hasPermission(Permission perm) {
                        return true;
                    }
                });
            }

            if (op && !player.isOp()) {
                player.setOp(true);
                restoreOp = true;
            }
            // endregion

            // region receive packet
            receivePacket(player, command);
            // endregion

            // region restore
            if (force) {
                fieldHumanEntity_perm.set(player, backup);
            }
            if (restoreOp) player.setOp(false);
            // endregion
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void receivePacket(Player player, String command) throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchFieldException {
        if (!command.startsWith("/")) {
            command = "/" + command;
        }

        // region init reflection
        Class classPacketPlayInChat = ReflectionUtil.getNMSClass("PacketPlayInChat");
        Constructor packetConstructor = ReflectionUtil.getConstructor(classPacketPlayInChat, String.class);

        Class classCraftEntity = ReflectionUtil.getOBCClass("entity.CraftEntity");
        Class classEntityPlayer = ReflectionUtil.getNMSClass("EntityPlayer");
        Class classPlayerConnection = ReflectionUtil.getNMSClass("PlayerConnection");
        if (fieldCraftEntity_entity == null) {
            fieldCraftEntity_entity = classCraftEntity.getDeclaredField("entity");
            fieldCraftEntity_entity.setAccessible(true);
        }
        if (fieldEntityPlayer_playerConnection == null) {
            fieldEntityPlayer_playerConnection = ReflectionUtil.getField(classEntityPlayer, "playerConnection");
            fieldEntityPlayer_playerConnection.setAccessible(true);
        }

        Method methodPlayerConnection_a = ReflectionUtil.getMethod(classPlayerConnection, "a", classPacketPlayInChat);
        // endregion

        // region init references
        Object entityPlayer = fieldCraftEntity_entity.get(player);
        Object playerConnection = fieldEntityPlayer_playerConnection.get(entityPlayer);
        // endregion

        // region create packet
        Object packet = packetConstructor.newInstance(command);
        // endregion

        // region receive packet
        methodPlayerConnection_a.invoke(playerConnection, packet);
        // endregion
    }

    public String arrangeCmd(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            sb.append(args[i]);
            if (i != args.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
