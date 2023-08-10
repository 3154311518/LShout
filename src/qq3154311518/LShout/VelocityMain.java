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

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.UserManager;

@Plugin(id = "lshout")
public class VelocityMain {
    private final ProxyServer server;

    private Connection connection;
    private Properties config;

    private HashMap<String, Long> shoutCooldown = new HashMap<>();
    private HashMap<String, Long> callCooldown = new HashMap<>();
    
    @Inject
    public VelocityMain(ProxyServer server) {
        this.server = server;
        File getDataFolder = new File("plugins/LShout");
        if (!getDataFolder.exists()) {
            getDataFolder.mkdir();
        }
        
        File file = new File(getDataFolder, "config.properties");
        config = new Properties();

        try {
        if (!file.exists()) {
            InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties");
            Files.copy(in, file.toPath());
        }
        config.load(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + config.getProperty("MySQL"));
        Statement statement = connection.createStatement();
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS lshout (playername VARCHAR(16), amount INT)");
        statement.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        server.getCommandManager().register("lb", new ShoutCommand());
        server.getCommandManager().register("zh", new CallCommand());        
    }

    public class ShoutCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!(source instanceof Player)) return;

            Player player = (Player) source;

            if (args.length == 0) {
                player.sendMessage(Component.text(config.getProperty("CoreMessage.Help")));
                return;
            }

            if (args[0].equalsIgnoreCase("lk")) {
                try {
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM lshout WHERE playername=?");
                    statement.setString(1, player.getUsername());
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        int amount = result.getInt("amount");
                        player.sendMessage(Component.text(config.getProperty("CoreMessage.ShoutLook").replace("%ShoutAmount%", String.valueOf(amount))));
                    } else {
                        player.sendMessage(Component.text(config.getProperty("CoreMessage.ShoutNot")));
                    }
                    result.close();
                    statement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            if (!player.hasPermission("lshout.shout")) {
                if (shoutCooldown.containsKey(player.getUsername())) {
                    long timeLeft = ((shoutCooldown.get(player.getUsername()) / 1000) + Integer.parseInt(config.getProperty("Settings.ShoutCooling")) - (System.currentTimeMillis() / 1000));
                    if (timeLeft > 0) {
                        player.sendMessage(Component.text(config.getProperty("CoreMessage.ShoutCooling").replace("%ShoutCooling%", String.valueOf(timeLeft))));
                        return;
                    }
                }
                try {
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM lshout WHERE playername=?");
                    statement.setString(1, player.getUsername());
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        int amount = result.getInt("amount");
                        if (amount <= 0) {
                            player.sendMessage(Component.text(config.getProperty("CoreMessage.ShoutNot")));
                            return;
                        }
                        PreparedStatement updateStatement = connection.prepareStatement("UPDATE lshout SET amount=? WHERE playername=?");
                        updateStatement.setInt(1, amount - 1);
                        updateStatement.setString(2, player.getUsername());
                        updateStatement.executeUpdate();
                        updateStatement.close();
                    } else {
                        player.sendMessage(Component.text(config.getProperty("CoreMessage.ShoutNot")));
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

            Component component = Component.text(config.getProperty("CoreMessage.ShoutFormat").replace("%player%", player.getUsername()).replace("%message%", message.toString().trim()).replace("%luckperms_prefix%", prefix).replace("%luckperms_suffix%", suffix), NamedTextColor.WHITE);
            for (Player p : server.getAllPlayers()) {
                p.sendMessage(component);
            }

            if (!player.hasPermission("lshout.shout")) {
                shoutCooldown.put(player.getUsername(), System.currentTimeMillis());
            }
        }
    }

    public class CallCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!(source instanceof Player)) return;

            Player player = (Player) source;

            if (args.length == 0) {
                player.sendMessage(Component.text(config.getProperty("CoreMessage.Help")));
                return;
            }

            if (!player.hasPermission("lshout.call")) {
                if (callCooldown.containsKey(player.getUsername())) {
                    long timeLeft = ((callCooldown.get(player.getUsername()) / 1000) + Integer.parseInt(config.getProperty("Settings.CallCooling")) - (System.currentTimeMillis() / 1000));
                    if (timeLeft > 0) {
                        player.sendMessage(Component.text(config.getProperty("CoreMessage.CallCooling").replace("%CallCooling%", String.valueOf(timeLeft))));
                        return;
                    }
                }
                StringBuilder message = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    message.append(args[i]).append(" ");
                }
                if (message.length() > Integer.parseInt(config.getProperty("Settings.CallLength"))) {
                    player.sendMessage(Component.text(config.getProperty("CoreMessage.CallLength").replace("%CallLength%", String.valueOf(Integer.parseInt(config.getProperty("Settings.CallLength"))))));
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

            Component component = Component.text(config.getProperty("CoreMessage.CallFormat").replace("%player%", player.getUsername()).replace("%message%", message.toString().trim()).replace("%server%", player.getCurrentServer().get().getServerInfo().getName()).replace("%luckperms_prefix%", prefix).replace("%luckperms_suffix%", suffix), NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.runCommand("/server " + player.getCurrentServer().get().getServerInfo().getName()))
                    .hoverEvent(HoverEvent.showText(Component.text(config.getProperty("CoreMessage.CallHoverShow").replace("%server%", player.getCurrentServer().get().getServerInfo().getName()))));
            for (Player p : server.getAllPlayers()) {
                p.sendMessage(component);
            }

            if (!player.hasPermission("lshout.call")) {
                callCooldown.put(player.getUsername(), System.currentTimeMillis());
            }
        }
    }
    @Subscribe
    public void CommandExecuteEvent(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;
        Player player = (Player) event.getCommandSource();
        String[] disableServers = config.getProperty("Settings.DisableServer").split(",");
        if (Arrays.asList(disableServers).contains(player.getCurrentServer().get().getServerInfo().getName())) {
            if (event.getCommand().startsWith("server")) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
            }
        }
    }    
}