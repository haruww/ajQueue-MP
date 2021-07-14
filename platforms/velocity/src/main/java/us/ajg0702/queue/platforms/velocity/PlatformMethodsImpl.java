package us.ajg0702.queue.platforms.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import us.ajg0702.queue.api.PlatformMethods;
import us.ajg0702.queue.api.commands.IBaseCommand;
import us.ajg0702.queue.api.commands.ICommandSender;
import us.ajg0702.queue.api.players.AdaptedPlayer;
import us.ajg0702.queue.api.players.QueuePlayer;
import us.ajg0702.queue.api.queues.QueueServer;
import us.ajg0702.queue.platforms.velocity.players.VelocityPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public class PlatformMethodsImpl implements PlatformMethods {

    final ProxyServer proxyServer;
    final Logger logger;
    final VelocityQueue plugin;

    public PlatformMethodsImpl(VelocityQueue plugin, ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.plugin = plugin;
    }

    @Override
    public void sendJoinQueueChannelMessages(QueueServer queueServer, QueuePlayer queuePlayer) {
        AdaptedPlayer player = queuePlayer.getPlayer();
        if(player == null) return;
        player.sendMessage(Component.text());
    }

    @Override
    public void sendPluginMessage(AdaptedPlayer player, String channel, String... data) {
        if(player == null) return;
        Player velocityPlayer = ((VelocityPlayer) player).getHandle();
    }

    @Override
    public AdaptedPlayer senderToPlayer(ICommandSender sender) {
        return new VelocityPlayer((Player) sender.getHandle());
    }

    @Override
    public String getPluginVersion() {
        Optional<PluginContainer> plugin = proxyServer.getPluginManager().getPlugin("ajqueue");
        if(!plugin.isPresent()) return "?E";
        Optional<String> version = plugin.get().getDescription().getVersion();
        if(!version.isPresent()) return "?V";
        return version.get();
    }

    @Override
    public List<AdaptedPlayer> getOnlinePlayers() {
        List<AdaptedPlayer> players = new ArrayList<>();
        for(Player player : proxyServer.getAllPlayers()) {
            players.add(new VelocityPlayer(player));
        }
        return players;
    }

    @Override
    public List<String> getPlayerNames(boolean lowercase) {
        List<String> players = new ArrayList<>();
        for(Player player : proxyServer.getAllPlayers()) {
            if(lowercase) {
                players.add(player.getUsername().toLowerCase(Locale.ROOT));
            } else {
                players.add(player.getUsername());
            }
        }
        return players;
    }

    @Override
    public AdaptedPlayer getPlayer(String name) {
        Optional<Player> player = proxyServer.getPlayer(name);
        if(!player.isPresent()) {
            System.out.println("Player "+name+" not found");
            return null;
        }
        return new VelocityPlayer(player.get());
    }

    @Override
    public List<String> getServerNames() {
        List<String> names = new ArrayList<>();
        for(RegisteredServer server : proxyServer.getAllServers()) {
            names.add(server.getServerInfo().getName());
        }
        return names;
    }

    @Override
    public List<IBaseCommand> getCommands() {
        return plugin.commands;
    }
}
