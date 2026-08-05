package ru.spliterash.musicbox.commands.subcommands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.commands.SubCommand;
import ru.spliterash.musicbox.utils.FoliaUtils;

public class ReloadExecutor implements SubCommand {
    @Override
    public void execute(CommandSender sender, String[] args) {
        FoliaUtils.runAsync(() -> {
            MusicBox.getInstance().reloadPlugin();
            if (sender instanceof Player) {
                Player player = (Player) sender;
                // Chat feedback must be sent on the player's own region thread.
                FoliaUtils.runAtPlayer(player, () -> player.sendMessage(ChatColor.GREEN + "Reloaded"));
            } else {
                // Console sender is thread-safe.
                sender.sendMessage(ChatColor.GREEN + "Reloaded");
            }
        });

    }

    @Override
    public boolean canExecute(CommandSender sender) {
        return sender.hasPermission("musicbox.admin");
    }
}
