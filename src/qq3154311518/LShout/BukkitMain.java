package qq3154311518.LShout;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitMain extends JavaPlugin implements CommandExecutor {
    private Connection connection;
    private Properties config;

    @Override
    public void onEnable() {
        getCommand("lbm").setExecutor(this);

        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        
        File file = new File(getDataFolder(), "config.properties");
        config = new Properties();

        try {
        if (!file.exists()) {
            saveResource("config.properties", false);
        }
        config.load(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        connection = DriverManager.getConnection("jdbc:mysql://" + config.getProperty("MySQL"));
        Statement statement = connection.createStatement();
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS lshout (playername VARCHAR(16), amount INT)");
        statement.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("lbm")) {
            if (args.length == 0) {
                sender.sendMessage(config.getProperty("BukkitMessage.Help"));
                return true;
            }
            if (args.length == 3) {
                String subcommand = args[0];
                String playername = args[1];
                int amount = Integer.parseInt(args[2]);
                try {
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM lshout WHERE playername=?");
                    statement.setString(1, playername);
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        int currentAmount = result.getInt("amount");
                        if (subcommand.equalsIgnoreCase("give")) {
                            currentAmount += amount;
                            statement = connection.prepareStatement("UPDATE lshout SET amount=? WHERE playername=?");
                            statement.setInt(1, currentAmount);
                            statement.setString(2, playername);
                            statement.executeUpdate();
                            sender.sendMessage(config.getProperty("BukkitMessage.Give").replace("%player%", playername).replace("%amount%", String.valueOf(amount)));
                        } else if (subcommand.equalsIgnoreCase("take")) {
                            currentAmount -= amount;
                            statement = connection.prepareStatement("UPDATE lshout SET amount=? WHERE playername=?");
                            statement.setInt(1, currentAmount);
                            statement.setString(2, playername);
                            statement.executeUpdate();
                            sender.sendMessage(config.getProperty("BukkitMessage.Take").replace("%player%", playername).replace("%amount%", String.valueOf(amount)));
                        } else if (subcommand.equalsIgnoreCase("set")) {
                            statement = connection.prepareStatement("UPDATE lshout SET amount=? WHERE playername=?");
                            statement.setInt(1, amount);
                            statement.setString(2, playername);
                            statement.executeUpdate();
                            sender.sendMessage(config.getProperty("BukkitMessage.Set").replace("%player%", playername).replace("%amount%", String.valueOf(amount)));
                        }
                    } else {
                        if (subcommand.equalsIgnoreCase("give") || subcommand.equalsIgnoreCase("set")) {
                            statement = connection.prepareStatement("INSERT INTO lshout (playername, amount) VALUES (?, ?)");
                            statement.setString(1, playername);
                            statement.setInt(2, amount);
                            statement.executeUpdate();
                            sender.sendMessage(config.getProperty("BukkitMessage.Give").replace("%player%", playername).replace("%amount%", String.valueOf(amount)));
                        } else if (subcommand.equalsIgnoreCase("take")) {
                            sender.sendMessage(config.getProperty("BukkitMessage.Not").replace("%player%", playername));
                        }
                    }
                    result.close();
                    statement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("look")) {
                String playername = args[1];
                try {
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM lshout WHERE playername=?");
                    statement.setString(1, playername);
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {
                        int amount = result.getInt("amount");
                        sender.sendMessage(config.getProperty("BukkitMessage.Look").replace("%player%", playername).replace("%amount%", String.valueOf(amount)));
                    } else {
                        sender.sendMessage(config.getProperty("BukkitMessage.Not").replace("%player%", playername));
                    }
                    result.close();
                    statement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                sender.sendMessage(config.getProperty("BukkitMessage.Help"));
            }
        }
        return true;
    }
}