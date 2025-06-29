package me.jaron.minion;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayersConnection implements Listener {

    String pluginDevelopment = ChatColor.AQUA + "Thank you for downloading my minecraft spigot plugin!\n"
            + ChatColor.GRAY + "This was developed by JA_RON in submission to the hackclub challenge!\n"
            + ChatColor.RED + "Click > "+ ChatColor.YELLOW + ChatColor.UNDERLINE +"https://github.com/Jaron-Wilson/SpigotMinionsPlugin/\n"
            + ChatColor.LIGHT_PURPLE + "Check out the hackclub vote here: \n"
            + ChatColor.RED +"Click > "+ ChatColor.YELLOW + ChatColor.UNDERLINE + " https://summer.hackclub.com/projects/1247 \n"

            + ChatColor.DARK_GRAY + "----------------------------------------------------\n"
            + ChatColor.RESET + "Welcome your commands are the following commands:\n"
            + ChatColor.GOLD + "/createminion " + ChatColor.GRAY + "Spawn a minion at your current block\n"
            + ChatColor.GOLD + "/showminioninventory "+ ChatColor.GRAY + "Show the closest minion inventory\n"
            + ChatColor.GOLD + "/giveminionegg "+ ChatColor.GRAY + "Now you can spawn minions at any block\n"
            + ChatColor.GOLD + "/minionautomation " + ChatColor.RED + "(start | stop) " + ChatColor.GRAY + "Start or Stop those minions (if any else it will run without minions for ya) can also start without " + ChatColor.RED +"(start)" +"\n"
            + ChatColor.GOLD + "/collectall "+ ChatColor.GRAY + "Take " + ChatColor.RED + ChatColor.BOLD +"ALL " + ChatColor.GRAY +"minions storages that you own chest and all\n"
            + ChatColor.GOLD + "/deleteallminions " + ChatColor.GRAY + ChatColor.BOLD + "Will delete all the minions you own there will be a confirmation for you\n"
            + ChatColor.GOLD + "/getbundle " + ChatColor.GRAY + "You need this! Please run this first it will solve all problems you have\n"
            + ChatColor.DARK_GRAY + "----------------------------------------------------\n"
            + ChatColor.AQUA + "Have Fun!"

            ;


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(pluginDevelopment);
        event.setJoinMessage(ChatColor.RED + "\n\nWatch out for player: " + player.getName() + " has joined the server!");
    }

    public String getPluginDevelopment() {
        return pluginDevelopment;
    }

    public void setPluginDevelopment(String pluginDevelopment) {
        this.pluginDevelopment = pluginDevelopment;
    }
}
