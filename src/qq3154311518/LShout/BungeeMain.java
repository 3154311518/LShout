package qq3154311518.LShout;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.UserManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class BungeeMain extends Plugin implements Listener {
    private Connection connection;
    private Properties config;

    private HashMap<String, Long> shoutCooldown = new HashMap<>();
    private HashMap<String, Long> callCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        
        File file = new File(getDataFolder(), "config.properties");
        config = new Properties();

        try {
        if (!file.exists()) {
            InputStream in = getResourceAsStream("config.properties");
            Files.copy(in, file.toPath());
        }
        config.load(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        connection = DriverManager.getConnection("jdbc:mysql://" + config.getProperty("MySQL"));
        Statement statement = connection.createStatement();
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS lshout (playername VARCHAR(16), amount INT)");
        statement.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getProxy().getPluginManager().registerCommand(this, new ShoutCommand());
        getProxy().getPluginManager().registerCommand(this, new CallCommand());
        getProxy().getPluginManager().registerListener(this, this);
    }

    public class ShoutCommand extends Command {
        public ShoutCommand() {
            super("lb");
        }
    
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) return;
    
            ProxiedPlayer player = (ProxiedPlayer) sender;
    
            if (args.length == 0) {
                player.sendMessage(new TextComponent(config.getProperty("CoreMessage.Help")));
                return;
            }
    
            if (args[0].equalsIgnoreCase("lk")) {
                try {
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM lshout WHERE playername=?");
                    statement.setString(1, player.getName());
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        int amount = result.getInt("amount");
                        player.sendMessage(new TextComponent(config.getProperty("CoreMessage.ShoutLook").replace("%ShoutAmount%", String.valueOf(amount))));
                    } else {
                        player.sendMessage(new TextComponent(config.getProperty("CoreMessage.ShoutNot")));
                    }
                    result.close();
                    statement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }
    
            if (!player.hasPermission("lshout.shout")) {
                if (shoutCooldown.containsKey(player.getName())) {
                    long timeLeft = ((shoutCooldown.get(player.getName()) / 1000) + Integer.parseInt(config.getProperty("Settings.ShoutCooling")) - (System.currentTimeMillis() / 1000));
                    if (timeLeft > 0) {
                        player.sendMessage(new TextComponent(config.getProperty("CoreMessage.ShoutCooling").replace("%ShoutCooling%", String.valueOf(timeLeft))));
                        return;
                    }
                }
                try {
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM lshout WHERE playername=?");
                    statement.setString(1, player.getName());
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        int amount = result.getInt("amount");
                        if (amount <= 0) {
                            player.sendMessage(new TextComponent(config.getProperty("CoreMessage.ShoutNot")));
                            return;
                        }
                        PreparedStatement updateStatement = connection.prepareStatement("UPDATE lshout SET amount=? WHERE playername=?");
                        updateStatement.setInt(1, amount - 1);
                        updateStatement.setString(2, player.getName());
                        updateStatement.executeUpdate();
                        updateStatement.close();
                    } else {
                        player.sendMessage(new TextComponent(config.getProperty("CoreMessage.ShoutNot")));
                        return;
                    }
                    result.close();
                    statement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
    
            StringBuilder message = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                message.append(args[i]).append(" ");
            }
    
            UserManager userManager = LuckPermsProvider.get().getUserManager();
            ContextManager contextManager = LuckPermsProvider.get().getContextManager();
            CachedMetaData metaData = userManager.getUser(player.getUniqueId()).getCachedData().getMetaData(contextManager.getQueryOptions(player));
            String prefix = metaData.getPrefix() != null ? metaData.getPrefix().replace('&', '§') : "";
            String suffix = metaData.getSuffix() != null ? metaData.getSuffix().replace('&', '§') : "";
    
            TextComponent component = new TextComponent(config.getProperty("CoreMessage.ShoutFormat").replace("%player%", player.getName()).replace("%message%", message.toString().trim()).replace("%luckperms_prefix%", prefix).replace("%luckperms_suffix%", suffix));
            for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
                p.sendMessage(component);
            }
    
            if (!player.hasPermission("lshout.shout")) {
                shoutCooldown.put(player.getName(), System.currentTimeMillis());
            }
        }
    }
    
    public class CallCommand extends Command {
        public CallCommand() {
            super("zh");
        }
    
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) return;
    
            ProxiedPlayer player = (ProxiedPlayer) sender;
    
            if (args.length == 0) {
                player.sendMessage(new TextComponent(config.getProperty("CoreMessage.Help")));
                return;
            }
    
            if (!player.hasPermission("lshout.call")) {
                if (callCooldown.containsKey(player.getName())) {
                    long timeLeft = ((callCooldown.get(player.getName()) / 1000) + Integer.parseInt(config.getProperty("Settings.CallCooling")) - (System.currentTimeMillis() / 1000));
                    if (timeLeft > 0) {
                        player.sendMessage(new TextComponent(config.getProperty("CoreMessage.CallCooling").replace("%CallCooling%", String.valueOf(timeLeft))));
                        return;
                    }
                }
                StringBuilder message = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    message.append(args[i]).append(" ");
                }
                if (message.length() > Integer.parseInt(config.getProperty("Settings.CallLength"))) {
                    player.sendMessage(new TextComponent(config.getProperty("CoreMessage.CallLength").replace("%CallLength%", String.valueOf(Integer.parseInt(config.getProperty("Settings.CallLength"))))));
                    return;
                }
            }
    
            StringBuilder message = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                message.append(args[i]).append(" ");
            }
    
            UserManager userManager = LuckPermsProvider.get().getUserManager();
            ContextManager contextManager = LuckPermsProvider.get().getContextManager();
            CachedMetaData metaData = userManager.getUser(player.getUniqueId()).getCachedData().getMetaData(contextManager.getQueryOptions(player));
            String prefix = metaData.getPrefix() != null ? metaData.getPrefix().replace('&', '§') : "";
            String suffix = metaData.getSuffix() != null ? metaData.getSuffix().replace('&', '§') : "";
    
            TextComponent component = new TextComponent(config.getProperty("CoreMessage.CallFormat").replace("%player%", player.getName()).replace("%message%", message.toString().trim()).replace("%server%", player.getServer().getInfo().getName()).replace("%luckperms_prefix%", prefix).replace("%luckperms_suffix%", suffix));
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/server " + player.getServer().getInfo().getName()));
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent(config.getProperty("CoreMessage.CallHoverShow").replace("%server%", player.getServer().getInfo().getName()))}));
            for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
                p.sendMessage(component);
            }
    
            if (!player.hasPermission("lshout.call")) {
                callCooldown.put(player.getName(), System.currentTimeMillis());
            }
        }
    }
    @EventHandler
    public void ChatEvent(ChatEvent event) {
        if (event.getSender() instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) event.getSender();
            String[] disableServers = config.getProperty("Settings.DisableServer").split(",");
            if (Arrays.asList(disableServers).contains(player.getServer().getInfo().getName())) {
                if (event.getMessage().startsWith("/server")) {
                    event.setCancelled(true);
                }
            }
        }
    }
}