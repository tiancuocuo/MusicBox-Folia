package ru.spliterash.musicbox.commands.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.spliterash.musicbox.Lang;
import ru.spliterash.musicbox.commands.SubCommand;
import ru.spliterash.musicbox.customDiscs.CustomDiscManager;

/**
 * /musicbox upload &lt;name&gt; - spend a disc + 100 coins to open a web upload
 * slot for a custom .nbs song.
 */
public class UploadExecutor implements SubCommand {
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
        String name = String.join(" ", args).trim();
        if (name.isEmpty()) {
            sender.sendMessage(Lang.UPLOAD_USAGE.toString());
            return;
        }
        // keep absurdly long names out
        if (name.length() > 64)
            name = name.substring(0, 64);
        CustomDiscManager.getInstance().startUpload((Player) sender, name);
    }
}
