package ru.spliterash.musicbox.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.spliterash.musicbox.Lang;
import ru.spliterash.musicbox.commands.SubCommand;
import ru.spliterash.musicbox.customDiscs.CustomDiscManager;

/**
 * /musicbox givecd &lt;discId&gt; - re-give an uploaded disc for 50 coins.
 * Used by the clickable [给予] button in the chat disc list.
 */
public class MyGiveDiscExecutor implements SubCommand {
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.ONLY_PLAYERS.toString());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Lang.UPLOAD_USAGE.toString());
            return;
        }
        CustomDiscManager.getInstance().giveDisc((Player) sender, args[0]);
    }
}
