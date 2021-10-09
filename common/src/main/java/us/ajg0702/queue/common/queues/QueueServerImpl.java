package us.ajg0702.queue.common.queues;

import com.google.common.collect.ImmutableList;
import jdk.nashorn.internal.runtime.Debug;
import us.ajg0702.queue.api.players.AdaptedPlayer;
import us.ajg0702.queue.api.players.QueuePlayer;
import us.ajg0702.queue.api.queues.Balancer;
import us.ajg0702.queue.api.queues.QueueServer;
import us.ajg0702.queue.api.server.AdaptedServer;
import us.ajg0702.queue.api.server.AdaptedServerPing;
import us.ajg0702.queue.common.QueueMain;
import us.ajg0702.queue.common.players.QueuePlayerImpl;
import us.ajg0702.queue.common.queues.balancers.DefaultBalancer;
import us.ajg0702.queue.common.queues.balancers.MinigameBalancer;
import us.ajg0702.queue.common.utils.Debugger;
import us.ajg0702.utils.common.GenUtils;
import us.ajg0702.utils.common.Messages;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class QueueServerImpl implements QueueServer {

    private final String name;

    public QueueServerImpl(String name, QueueMain main, AdaptedServer server, List<QueuePlayer> previousPlayers) {
        this(name, main, Collections.singletonList(server), previousPlayers);
    }

    public QueueServerImpl(String name, QueueMain main, List<AdaptedServer> servers, List<QueuePlayer> previousPlayers) {
        this.name = name;
        this.servers = servers;
        this.main = main;

        List<String> types = main.getConfig().getStringList("balancer-types");
        for(String type : types) {
            int colon = type.indexOf(":");
            if(colon == -1) continue;
            String groupName = type.substring(0, colon);
            String balancerType = type.substring(colon+1);

            if(groupName.equals(name)) {
                boolean valid = true;
                switch(balancerType.toLowerCase(Locale.ROOT)) {
                    case "minigame":
                        balancer = new MinigameBalancer(this, main);
                        break;
                    default:
                        balancerType = "default";
                        balancer = new DefaultBalancer(this, main);
                }
                Debugger.debug("Using "+balancerType.toLowerCase(Locale.ROOT)+" balancer for "+name);
                break;
            }
        }
        if(balancer == null) {
            balancer = new DefaultBalancer(this, main);
            Debugger.debug("Using default balancer for "+name);
        }

        for(QueuePlayer queuePlayer : previousPlayers) {
            if(queuePlayer.getPlayer() == null) {
                addPlayer(
                        new QueuePlayerImpl(
                                queuePlayer.getUniqueId(),
                                queuePlayer.getName(),
                                this,
                                queuePlayer.getPriority(),
                                queuePlayer.getMaxOfflineTime()
                        )
                );
            } else {
                addPlayer(
                        new QueuePlayerImpl(
                                queuePlayer.getPlayer(),
                                this,
                                queuePlayer.getPriority(),
                                queuePlayer.getMaxOfflineTime()
                        )
                );
            }
        }
    }

    private final QueueMain main;

    private final HashMap<AdaptedServer, AdaptedServerPing> pings = new HashMap<>();

    private final List<AdaptedServer> servers;

    private final List<QueuePlayer> queue = new ArrayList<>();

    private List<Integer> supportedProtocols = new ArrayList<>();

    private Balancer balancer;


    private int playerCount;
    private int maxPlayers;

    private boolean online;

    private boolean paused;


    private long lastUpdate = 0;

    private int offlineTime = 0;

    private long lastSentTime = 0;

    private long lastOffline;


    boolean whitelisted = false;
    List<UUID> whitelistedUUIDs = new ArrayList<>();


    @Override
    public ImmutableList<QueuePlayer> getQueue() {
        return ImmutableList.copyOf(queue);
    }

    @Override
    public String getStatusString(AdaptedPlayer p) {
        Messages msgs = main.getMessages();

        if(getOfflineTime() > main.getConfig().getInt("offline-time")) {
            return msgs.getString("status.offline.offline");
        }

        if(!isOnline()) {
            return msgs.getString("status.offline.restarting");
        }

        if(isPaused()) {
            return msgs.getString("status.offline.paused");
        }

        if(p != null && isWhitelisted() && !getWhitelistedPlayers().contains(p.getUniqueId())) {
            return msgs.getString("status.offline.whitelisted");
        }

        if(isFull()) {
            return msgs.getString("status.offline.full");
        }

        if(p != null && !canAccess(p)) {
            return msgs.getString("status.offline.restricted");
        }


        return "online";
    }

    @Override
    public String getStatusString() {
        return getStatusString(null);
    }

    @Override
    public void updatePing() {
        boolean pingerDebug = main.getConfig().getBoolean("pinger-debug");
        HashMap<AdaptedServer, CompletableFuture<AdaptedServerPing>> pingsFutures = new HashMap<>();
        for(AdaptedServer server : servers) {
            if(pingerDebug) {
                main.getLogger().info("[pinger] ["+server.getServerInfo().getName()+"] sending ping");
            }
            pingsFutures.put(server, server.ping());
        }

        int i = 0;
        for(AdaptedServer server : pingsFutures.keySet()) {
            CompletableFuture<AdaptedServerPing> futurePing = pingsFutures.get(server);
            AdaptedServerPing ping = null;
            try {
                ping = futurePing.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                if(pingerDebug) {
                    main.getLogger().info("[pinger] ["+server.getServerInfo().getName()+"] offline:");
                    e.printStackTrace();
                }
            }
            if(ping != null && pingerDebug) {
                main.getLogger().info("[pinger] ["+server.getServerInfo().getName()+"] online. motd: "+ping.getPlainDescription()+"  players: "+ping.getPlayerCount()+"/"+ping.getMaxPlayers());
            } else if(ping == null && pingerDebug) {
                main.getLogger().info("[pinger] ["+server.getServerInfo().getName()+"] offline (unknown)");
            }

            pings.put(server, ping);
            i++;
            if(i == servers.size()) {
                int onlineCount = 0;
                playerCount = 0;
                maxPlayers = 0;
                for(AdaptedServer pingedServer : pings.keySet()) {
                    AdaptedServerPing serverPing = pings.get(pingedServer);
                    if(serverPing == null || serverPing.getPlainDescription() == null) {
                        if(serverPing != null) {
                            pings.put(pingedServer, null);
                        }
                        continue;
                    }
                    if(serverPing.getPlainDescription().contains("ajQueue;whitelisted=")) {
                        if(servers.size() > 1) continue;

                        setWhitelisted(true);
                        List<UUID> uuids = new ArrayList<>();
                        for(String uuid : serverPing.getPlainDescription().substring(20).split(",")) {
                            if(uuid.isEmpty()) continue;
                            UUID parsedUUID;
                            try {
                                parsedUUID = UUID.fromString(uuid);
                            } catch(IllegalArgumentException e) {
                                main.getLogger().warn("UUID '"+uuid+"' in whitelist of "+getName()+" is invalid! "+e.getMessage());
                                continue;
                            }
                            uuids.add(parsedUUID);
                        }
                        setWhitelistedPlayers(uuids);
                    } else {
                        setWhitelisted(false);
                    }
                    onlineCount++;
                    playerCount += serverPing.getPlayerCount();
                    maxPlayers += serverPing.getMaxPlayers();
                }
                online = onlineCount > 0;

                if(lastUpdate == -1) {
                    lastUpdate = System.currentTimeMillis();
                    offlineTime = 0;
                } else {
                    int timesincelast = (int) Math.round((System.currentTimeMillis() - lastUpdate*1.0)/1000);
                    lastUpdate = System.currentTimeMillis();
                    if(!online) {
                        offlineTime += timesincelast;
                    } else {
                        offlineTime = 0;
                    }
                }
            }

            if(pingerDebug) {
                main.getLogger().info("[pinger] ["+server.getServerInfo().getName()+"] Success");
            }
        }
    }

    @Override
    public int getOfflineTime() {
        return offlineTime;
    }

    @Override
    public long getLastSentTime() {
        return System.currentTimeMillis() - lastSentTime;
    }
    @Override
    public void setLastSentTime(long lastSentTime) {
        this.lastSentTime = lastSentTime;
    }

    @Override
    public boolean isWhitelisted() {
        return whitelisted;
    }

    @Override
    public void setWhitelisted(boolean whitelisted) {
        this.whitelisted = whitelisted;
    }

    @Override
    public ImmutableList<UUID> getWhitelistedPlayers() {
        return ImmutableList.copyOf(whitelistedUUIDs);
    }

    @Override
    public synchronized void setWhitelistedPlayers(List<UUID> whitelistedPlayers) {
        whitelistedUUIDs = whitelistedPlayers;
    }

    @Override
    public boolean isJoinable(AdaptedPlayer p) {
        if(p != null) {
            if (isWhitelisted() && !whitelistedUUIDs.contains(p.getUniqueId())) {
                return false;
            }
            if (isFull() && !canJoinFull(p)) {
                return false;
            }
        }
        return isOnline() &&
                canAccess(p) &&
                !isPaused();
    }

    @Override
    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public boolean isOnline() {
        if(System.currentTimeMillis()-lastOffline <= (main.getConfig().getInt("wait-after-online")*1000) && online) {
            return false;
        }
        if(!online) {
            lastOffline = System.currentTimeMillis();
        }
        return online;
    }

    @Override
    public boolean justWentOnline() {
        return System.currentTimeMillis()-lastOffline <= (main.getConfig().getDouble("wait-time")) && online;
    }

    @Override
    public boolean isFull() {
        return playerCount >= maxPlayers;
    }

    @Override
    public synchronized void removePlayer(QueuePlayer player) {
        queue.remove(player);
    }

    @Override
    public void removePlayer(AdaptedPlayer player) {
        QueuePlayer queuePlayer = findPlayer(player);
        if(queuePlayer == null) return;
        removePlayer(queuePlayer);
    }

    @Override
    public void addPlayer(QueuePlayer player) {
        addPlayer(player, -1);
    }

    @Override
    public synchronized void addPlayer(QueuePlayer player, int position) {
        if(!player.getQueueServer().equals(this) || queue.contains(player)) return;

        if(position >= 0) {
            queue.add(position, player);
        } else {
            queue.add(player);
        }
    }

    @Override
    public void sendPlayer() {
        main.getQueueManager().sendPlayers(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean canAccess(AdaptedPlayer ply) {
        if(ply == null) return true;
        for(AdaptedServer si : servers) {
            if(si.canAccess(ply)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getAlias() {
        return main.getAliasManager().getAlias(getName());
    }

    @Override
    public ImmutableList<AdaptedServer> getServers() {
        return ImmutableList.copyOf(servers);
    }

    @Override
    public ImmutableList<String> getServerNames() {
        List<String> names = new ArrayList<>();
        for(AdaptedServer server : servers) {
            names.add(server.getName());
        }
        return ImmutableList.copyOf(names);
    }

    @Override
    public boolean isGroup() {
        return servers.size() > 1;
    }

    @Override
    public QueuePlayer findPlayer(AdaptedPlayer player) {
        return findPlayer(player.getUniqueId());
    }
    @Override
    public synchronized QueuePlayer findPlayer(UUID uuid) {
        for(QueuePlayer queuePlayer : queue) {
            if(queuePlayer.getUniqueId().toString().equals(uuid.toString())) {
                return queuePlayer;
            }
        }
        return null;
    }

    @Override
    public AdaptedServer getIdealServer(AdaptedPlayer player) {
        return getBalancer().getIdealServer(player);
    }

    @Override
    public HashMap<AdaptedServer, AdaptedServerPing> getLastPings() {
        return new HashMap<>(pings);
    }

    @Override
    public List<Integer> getSupportedProtocols() {
        return new ArrayList<>(supportedProtocols);
    }

    @Override
    public void setSupportedProtocols(List<Integer> list) {
        supportedProtocols = new ArrayList<>(list);
    }

    @Override
    public Balancer getBalancer() {
        return balancer;
    }

    @Override
    public boolean canJoinFull(AdaptedPlayer player) {
        return
                player.hasPermission("ajqueue.joinfull") ||
                player.hasPermission("ajqueue.joinfullserver."+name) ||
                player.hasPermission("ajqueue.joinfullandbypassserver."+name) ||
                player.hasPermission("ajqueue.joinfullandbypass");
    }
}
