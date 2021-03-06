/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.alexneises.hardcorebans;

/**
 *
 * @author Neises
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.*;
/**
 *
 * @author Neises
 */
public class HardcoreBans extends JavaPlugin implements Listener
{
    private FileConfiguration banStorage;
    private final Integer banDatabaseLock = 31337;
    private boolean suppressDeathEvents = false;
    private Connection conn;
    private Statement st;
    
    @Override
    public void onEnable()
    {
        getConfig().options().copyDefaults(true);
//        reloadBanDB();
        getServer().getPluginManager().registerEvents(this, this);
        if(getConfig().getString("mysql").toLowerCase().equals("true"))
        {
            modifyDatabase();
        }
    }
    
    public void modifyDatabase()
    {
        String url = "jdbc:mysql://"+getConfig().getString("hostname")+":"+getConfig().getString("port")+"/";
        String driver = "com.mysql.jdbc.Driver";
        String username = getConfig().getString("username");
        String password = getConfig().getString("password");
        try
        {
            Class.forName(driver).newInstance();
            conn = DriverManager.getConnection(url,username,password);
            st = conn.createStatement();
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS hardcore_bans; USE hardcore_bans;");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hardcore_bans.bans (playername VARCHAR(256), bantime BIGINT;");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
    
    @Override
    public void onDisable()
    {
//        saveBanDB();
        if(getConfig().getString("mysql").toLowerCase().equals("true"))
        {
            try
            {
                conn.close();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        this.saveConfig();
    }
    
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event)
    {
        if(isEnabled())
        {
            Player joiningPlayer = event.getPlayer();
            Long banLiftTime = 0L;
            try
            {
                ResultSet rs = st.executeQuery("SELECT bantime FROM hardcore_bans.bans WHERE playername = '"+joiningPlayer.getName()+"';");
                while(rs.next())
                {
                    banLiftTime = rs.getLong("bantime");
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
            if(banLiftTime != null)
            {
                long rightNow = (System.currentTimeMillis() / 1000);
                long remainingBanTime = banLiftTime - rightNow;
                
                if(remainingBanTime > 0)
                {
                    String readableRemainingBanTime = longToReadableTime(remainingBanTime);
                    String rejoinMessage = getConfig().getString("rejoinmessage").replace("$time", readableRemainingBanTime);
                    
                    event.setKickMessage(rejoinMessage);
                    event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                    
                    getLogger().log(Level.INFO, String.format("%s tried to join but is banned for %s.", joiningPlayer.getName(),readableRemainingBanTime));
                }
                else
                {
                    unbanPlayer(joiningPlayer.getName());
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event)
    {
        if(isEnabled() && !suppressDeathEvents)
        {
            banPlayer(event.getEntity().getName(), getConfig().getLong("bantime"), event.getDeathMessage());
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if(command.getName().equalsIgnoreCase("db-ban"))
        {
            if(args.length == 2)
            {
                Player victim = getServer().getPlayer(args[0]);
                long banTime = Integer.parseInt(args[1]);
                
                if((victim != null) && killPlayer(victim.getName(), sender.getName()))
                {
                    sender.sendMessage(String.format("Successfully killed %s", victim.getName()));
                }
                else
                {
                    sender.sendMessage(String.format("Unable to kill %s", args[0]));
                }
                banPlayer(args[0], banTime, sender.getName(), null);
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(command.getName().equalsIgnoreCase("db-clear"))
        {
            if(args.length == 0)
            {
//                clearBanDB();
                sender.sendMessage("Banlist cleared.");
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(command.getName().equalsIgnoreCase("db-list"))
        {
            if(args.length == 0)
            {
                updateDB();
//                ArrayList<String> banMessages = new ArrayList<String>(banDatabase.size());
                List<String> players = new ArrayList<String>();
                try
                {
                    ResultSet rs = st.executeQuery("SELECT playername FROM hardcore_bans.bans WHERE 1;");
                    while(rs.next())
                    {
                        players.add(rs.getString("playername"));
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
                for(String targetPlayer : players)
                {
                    long remainingTime = 0L;
                    try
                    {
                        ResultSet rs = st.executeQuery("SELECT bantime FROM hardcore_bans.bans WHERE playername = '"+targetPlayer+"';");
                        while(rs.next())
                        {
                            remainingTime = rs.getLong("bantime") - System.currentTimeMillis() / 1000;
                            String readableRemainingTime = longToReadableTime(remainingTime);
//                            banMessages.add(String.format("%s is banned for %s", getName(targetPlayer), readableRemainingTime));
                        }
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
//                if(banMessages.isEmpty())
//                {
//                    sender.sendMessage("The banlist is empty.");
//                }
//                else
//                {
//                    for(String banMessage: banMessages)
//                    {
//                        sender.sendMessage(banMessage);
//                    }
//                }
                return true;
            }
            else if(args.length == 1)
            {
                updateDB();
                String targetPlayer = args[0];
                Long banLiftTime = 0L;
            try
            {
                ResultSet rs = st.executeQuery("SELECT bantime FROM hardcore_bans.bans WHERE playername = '"+targetPlayer+"';");
                while(rs.next())
                {
                    banLiftTime = rs.getLong("bantime");
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
                if(banLiftTime != null)
                {
                    long remainingTime = banLiftTime - (System.currentTimeMillis() / 1000);
                    String readableRemainingTime = longToReadableTime(remainingTime);
                    sender.sendMessage(String.format("%s is banned for %s", getName(targetPlayer), readableRemainingTime));
                }
                else
                {
                    sender.sendMessage(String.format("%s is not banned", targetPlayer));
                }
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(command.getName().equalsIgnoreCase("db-reload"))
        {
            if(args.length == 0)
            {
                reloadConfig();
//                reloadBanDB();
                sender.sendMessage("Reloaded banlist");
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(command.getName().equalsIgnoreCase("db-settime"))
        {
            if(args.length == 1)
            {
                int banTime = Integer.parseInt(args[0]);
                getConfig().set("bantime", banTime);
                getLogger().log(Level.INFO, String.format("%s set bantime to %d", sender.getName(), banTime));
                saveConfig();
                sender.sendMessage(String.format("Bantime was set to %s.", banTime));
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(command.getName().equalsIgnoreCase("db-unban"))
        {
            if(args.length == 1)
            {
                String targetPlayer = args[0];
                Long bantime = 0L;
                try
                {
                    ResultSet rs = st.executeQuery("SELECT bantime FROM hardcore_bans.bans WHERE playername = '"+targetPlayer+"';");
                    while(rs.next())
                    {
                        bantime = rs.getLong("bantime");
                        if(bantime != null)
                        {
                            unbanPlayer(targetPlayer);
                            sender.sendMessage(String.format("%s has been unbanned.", getName(targetPlayer)));
                        }
                        else
                        {
                            Player tempPlayer = getServer().getPlayer(targetPlayer);
                            if (tempPlayer != null) {
                                if (bantime != null) {
                                    unbanPlayer(tempPlayer.getName());
                                    sender.sendMessage(String.format("%s has been unbanned.", targetPlayer));
                                }
                                else
                                {
                                    sender.sendMessage(String.format("Neither %s nor %s is banned.", targetPlayer, tempPlayer.getName()));
                                }
                            }
                            else
                            {
                                sender.sendMessage(String.format("%s is not banned.", targetPlayer));
                            }
                        }
                        return true;
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                return false;
            }
        }
        else if(command.getName().equalsIgnoreCase("db-update"))
        {
            updateDB();
            sender.sendMessage("Database update complete");
            return true;
        }
        else
        {
            return false;
        }
        this.saveConfig();
        return true;
    }
    
    private File getBanDBFile()
    {
        return new File(getDataFolder(), "bans.yml");
    }
    
    private void updateDB() {
        int oldBans = 0;
        try
        {
            oldBans = st.executeUpdate("SELECT bantime FROM hardcore_bans.bans WHERE 1;");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        try
        {
            List<String> players = new ArrayList<String>();
            ResultSet rs = st.executeQuery("SELECT bantime FROM hardcore_bans.bans WHERE 1;");
            while(rs.next())
            {
                players.add(rs.getString("playername"));
            }
            for (String targetPlayer : players)
            {
                Long banLiftTime = 0L;
                try
                {
                    ResultSet newrs = st.executeQuery("SELECT bantime FROM hardcore_bans.bans WHERE playername = '"+targetPlayer+"';");
                    while(newrs.next())
                    {
                        banLiftTime = rs.getLong("bantime");
                        if (banLiftTime != null)
                        {
                            long remainingTime = banLiftTime - (System.currentTimeMillis() / 1000);
                            if (remainingTime <= 0)
                            {
                                unbanPlayer(targetPlayer.toLowerCase());
                            }
                        }
                        else
                        {
                            getLogger().log(Level.SEVERE, String.format("An error occurred while checking %s's ban.", targetPlayer));
                        }
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }        
//        getLogger().log(Level.INFO, String.format("Database maintenance complete, removed %d bans.", oldBans - banDatabase.size()));
    }
    
//    private void reloadBanDB() {
//        synchronized (banDatabaseLock) {
//            banStorage = YamlConfiguration.loadConfiguration(getBanDBFile());
//            banDatabase = new HashMap<String, Long>();
//            try
//            {
//                oldBans = st.executeUpdate("SELECT bantime FROM hardcore_bans.bans WHERE 1;");
//                MemorySection storedBanDatabase = (MemorySection) banStorage.get("banlist", null);
//                if (storedBanDatabase != null)
//                {
//                    Set<String> playerList = storedBanDatabase.getKeys(false);
//                    for (String player : playerList)
//                    {
//                        Object banLiftTime = storedBanDatabase.get(player.toLowerCase());
//                        if (banLiftTime instanceof Integer)
//                        {
//                            banDatabase.put(player.toLowerCase(), ((Integer) banLiftTime).longValue());
//                        }
//                        else if (banLiftTime instanceof Long)
//                        {
//                            banDatabase.put(player.toLowerCase(), (Long) banLiftTime);
//                        }
//                        else
//                        {
//                            getLogger().log(Level.SEVERE, String.format("Unable to load banLiftTime for %s, ignoring!", player));
//                        }
//                    }
//                }
//            }
//            catch (Exception ex)
//            {
//                ex.printStackTrace();
//            }
//        }
//        getLogger().log(Level.INFO, String.format("Loaded %d bans from ban storage.", banDatabase.size()));
//        updateDB();
//    }
//
//    private void saveBanDB() {
//        synchronized (banDatabaseLock) {
//            banStorage.set("banlist", banDatabase);
//            File banDBFile = getBanDBFile();
//            try {
//                banStorage.save(banDBFile);
//                getLogger().log(Level.INFO, "Banlist saved.");
//            } catch (IOException ex) {
//                getLogger().log(Level.SEVERE, String.format("Could not save banlist to %s!", banDBFile.getName()), ex);
//            }
//        }
//    }

//    private void clearBanDB() {
//        synchronized (banDatabaseLock) {
//            banDatabase = new HashMap<String, Long>();
//            getLogger().log(Level.INFO, "Banlist cleared");
//            saveBanDB();
//        }
//    }

    private boolean killPlayer(String victimName, String killerName) {
        Player victim = getServer().getPlayer(victimName);

        if (victim != null) {
            if (!victim.isDead()) {
                // Ensure that we don't accidentally catch this with our listener
                suppressDeathEvents = true;

                getServer().getPluginManager().callEvent(new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.SUICIDE, victim.getHealth()));

                victim.damage(victim.getHealth());
                suppressDeathEvents = false;
                getLogger().log(Level.INFO, String.format("%s killed by %s", victim.getName(), killerName));
            } else {
                getLogger().log(Level.INFO, String.format("%s wanted to kill %s but player was already dead", killerName, victim.getName()));
            }

            return true;
        } else {
            return false;
        }
    }

    private void banPlayer(String playerName, long banDuration, String reason) {
        banPlayer(playerName, banDuration, null, reason);
    }

    private String longToReadableTime(long time) {
        List<String> timeStrings = new ArrayList<String>(4);
        Long remainingSeconds = time;

        // A very dumb way to calculate this - but at least it's readable :)

        // Calculate days
        if (remainingSeconds > (60 * 60 * 24)) {
            long days = remainingSeconds / (60 * 60 * 24);
            if (days > 0) {
                if (days > 1) {
                    timeStrings.add(String.format("%d days", days));
                } else {
                    timeStrings.add(String.format("%d day", days));
                }

                remainingSeconds -= days * (60 * 60 * 24);
            }
        }

        // Calculate hours
        if (remainingSeconds > (60 * 60)) {
            long hours = remainingSeconds / (60 * 60);
            if (hours > 0) {
                if (hours > 1) {
                    timeStrings.add(String.format("%d hours", hours));
                } else {
                    timeStrings.add(String.format("%d hour", hours));
                }

                remainingSeconds -= hours * (60 * 60);
            }
        }

        // Calculate minutes
        if (remainingSeconds > 60) {
            long minutes = remainingSeconds / 60;
            if (minutes > 0) {
                if (minutes > 1) {
                    timeStrings.add(String.format("%d minutes", minutes));
                } else {
                    timeStrings.add(String.format("%d minute", minutes));
                }

                remainingSeconds -= minutes * 60;
            }
        }

        // Calculate seconds
        if (remainingSeconds > 0 || (timeStrings.isEmpty())) {
            if (remainingSeconds == 1) {
                timeStrings.add(String.format("%d second", remainingSeconds));
            } else {
                timeStrings.add(String.format("%d seconds", remainingSeconds));
            }
        }

        // Turn this into a nice, readable String
        // The code itself is not so nice, but the result will be :)

        if (timeStrings.size() > 1) {
            StringBuilder sb = new StringBuilder();

            sb.append(timeStrings.get(0));

            for (int i = 1; i < (timeStrings.size() - 1); i++) {
                sb.append(String.format(", %s", timeStrings.get(i)));
            }

            sb.append(String.format(" and %s", timeStrings.get(timeStrings.size() - 1)));

            return sb.toString();
        } else {
            return timeStrings.get(0);
        }
    }

    private void unbanPlayer(String playerName) {
        synchronized (banDatabaseLock) {
            try
            {
                st.executeUpdate("DELETE FROM hardcore_bans.bans WHERE playername='"+playerName+"';");
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        getLogger().log(Level.INFO, String.format("Unbanned %s", playerName));
    }

    private void banPlayer(String playerName, long banDuration, String senderName, String reason) {
        Player player = getServer().getPlayer(playerName);
        String humanReadableTime = longToReadableTime(banDuration);
        String kickReason = getConfig().getString("kickmessage").replace("$time", humanReadableTime);
        Long banLiftTime = (System.currentTimeMillis() / 1000) + banDuration;

        if (player != null) {
            player.getInventory().clear();
            player.kickPlayer(kickReason);
        }

        synchronized (banDatabaseLock) {
            if (player != null)
            {
                try
                {
                    st.executeUpdate("INSERT INTO hardcore_bans.bans (playername,bantime) VALUES ('"+player.getName()+"',"+banLiftTime+");");
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    st.executeUpdate("INSERT INTO hardcore_bans.bans (playername,bantime) VALUES ("+playerName+","+banLiftTime+");");
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            if (senderName == null) {
                // Player was banned for death
                getLogger().log(Level.INFO, String.format("%s and is banned for %s", reason, humanReadableTime));
            } else {
                getLogger().log(Level.INFO, String.format("%s banned %s for %s", senderName, playerName, humanReadableTime));

                // Notify sender
                Player sender = getServer().getPlayer(senderName);
                if (sender != null) {
                    if (player != null) {
                        sender.sendMessage(String.format("%s is now banned for %s.", player.getName(), humanReadableTime));
                    } else {
                        sender.sendMessage(String.format("%s is now banned for %s.", playerName, humanReadableTime));
                    }
                }
            }
//            saveBanDB();
        }
    }
    
    public static String getName(String UUID)
    {
        try
        {
            URL url = new URL("[url]https://api.mojang.com/user/profiles/[/url]"+UUID.replaceAll("-", "") + "/names");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String line = in.readLine();
            line = line.replace("[\"", "");
            line = line.replace("\"]", "");
            return line;
        }
        catch(Exception ex)
        {
            return null;
        }
    }
        
    public static String getUUIDs(String player)
    {
        try
        {
            URL url = new URL("[url]https://api.mojang.com/users/profiles/minecraft/[/url]" + player);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String Line;
            while((Line = in.readLine()) != null)
            {
                String uuid = Line.substring(7, 39);
                return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32);
            }
            in.close();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return null;
    }

    public static String getName(UUID uuid)
    {
        return getName(uuid.toString());
    }
    
    public static String getUUID(String player)
    {
        String strUUID = getUUIDs(player);
        return strUUID;
    }
}
