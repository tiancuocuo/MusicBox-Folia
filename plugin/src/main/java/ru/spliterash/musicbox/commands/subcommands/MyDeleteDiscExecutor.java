package ru.spliterash.musicbox.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.spliterash.musicbox.Lang;
import ru.spliterash.musicbox.commands.SubCommand;
import ru.spliterash.musicbox.customDiscs.CustomDiscManager;

/**
 * /musicbox delcd &lt;discId&gt; [confirm] - deletes an uploaded disc. The chat
 * [删除] button asks for a confirmation click first to avoid accidents.
 */
public class MyDeleteDiscExecutor implements SubCommand {
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
        boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
        CustomDiscManager manager = CustomDiscManager.getInstance();
        if (confirm)
            manager.deleteDisc((Player) sender, args[0]);
        else
            manager.confirmDelete((Player) sender, args[0]);
    }
}
