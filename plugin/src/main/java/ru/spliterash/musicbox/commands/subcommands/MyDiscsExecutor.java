package ru.spliterash.musicbox.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.spliterash.musicbox.Lang;
import ru.spliterash.musicbox.commands.SubCommand;
import ru.spliterash.musicbox.customDiscs.CustomDiscManager;

/**
 * /musicbox mydiscs - shows the player's custom discs as clickable chat lines.
 */
public class MyDiscsExecutor implements SubCommand {
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.ONLY_PLAYERS.toString());
            return;
        }
        CustomDiscManager.getInstance().sendDiscList((Player) sender);
    }
}
