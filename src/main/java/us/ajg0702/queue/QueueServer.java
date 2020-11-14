package us.ajg0702.queue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import us.ajg0702.utils.bungee.BungeeConfig;

public class QueueServer {
	
	String name;
	List<ServerInfo> servers;
	
	public QueueServer(String name, ServerInfo info) {
		init(name, Arrays.asList(info));
	}
	public QueueServer(String name, List<ServerInfo> infos) {
		init(name, infos);
	}
	private void init(String name, List<ServerInfo> infos) {
		if(Manager.getInstance() == null || Main.plugin.getConfig() == null) {
			ProxyServer.getInstance().getLogger()
			.warning("[ajQueue] Something is loading a QueueServer too early! The plugin hasnt fully loaded yet!");
		}
		this.name = name;
		this.servers = infos;
		update();
	}
	
	public void setInfos(List<ServerInfo> infos) {
		servers = infos;
	}
	
	public String getName() {
		return name;
	}
	public List<ServerInfo> getInfos() {
		return servers;
	}
	
	int offlineTime = 0;
	boolean online = false;
	int playercount = 0;
	int maxplayers = 0;
	long lastUpdate = -1;
	HashMap<ServerInfo, ServerPing> pings = new HashMap<>();
	public void update() {
		pings = new HashMap<>();
		for(final ServerInfo info : getInfos()) {
			if(Main.plugin.getConfig().getBoolean("pinger-debug")) {
				Main.plugin.getLogger().info("[pinger] ["+info.getName()+"] sending ping");
			}
			info.ping(new Callback<ServerPing>() {
				@Override
				public void done(ServerPing result, Throwable error) {
					if(Manager.getInstance() == null || Main.plugin.getConfig() == null) {
						ProxyServer.getInstance().getLogger()
						.warning("[ajQueue] Something used update() too early! The plugin hasnt fully loaded yet!");
						return;
					}
					boolean online = error == null;
					BungeeConfig config = Main.plugin.getConfig();
					
					if(config.getBoolean("pinger-debug")) {
						if(error != null) {
							ProxyServer.getInstance().getLogger().info("[ajQueue] [pinger] ["+name+"] Status: "+online+".  Error: ");
							error.printStackTrace();
						} else {
							ProxyServer.getInstance().getLogger().info("[ajQueue] [pinger] ["+name+"] Status: "+online+".  motd: "
						+result.getDescriptionComponent()+"  players:"+result.getPlayers());
						}
					}
					
					
					pings.put(info, online ? result : null);
					if(pings.size() == servers.size()) allDonePing();
				}
			});
		}
	}
	
	public HashMap<ServerInfo, ServerPing> getLastPings() {
		return pings;
	}
	
	private void allDonePing() {
		int onlineCount = 0;
		playercount = 0;
		maxplayers = 0;
		for(ServerInfo info : pings.keySet()) {
			ServerPing ping = pings.get(info);
			if(ping == null) {
				continue;
			}
			onlineCount++;
			playercount += ping.getPlayers().getOnline();
			maxplayers += ping.getPlayers().getMax();
		}
		online = onlineCount > 0;
		
		if(lastUpdate == -1) {
			lastUpdate = System.currentTimeMillis();
			offlineTime = 0;
		} else {
			int timesincelast = Math.round((System.currentTimeMillis() - lastUpdate)/1000);
			lastUpdate = System.currentTimeMillis();
			if(!online) {
				offlineTime += timesincelast;
			} else {
				offlineTime = 0;
			}
		}
	}
	
	public int getOfflineTime() {
		return offlineTime;
	}
	long lastOffline = 0;
	public boolean isOnline() {
		BungeeConfig config = Main.plugin.getConfig();
		if(System.currentTimeMillis()-lastOffline <= (config.getInt("wait-after-online")*1000) && online) {
			return false;
		}
		if(!online) {
			lastOffline = System.currentTimeMillis();
		}
		return online;
	}
	
	public boolean justWentOnline() {
		BungeeConfig config = Main.plugin.getConfig();
		return System.currentTimeMillis()-lastOffline <= (config.getDouble("wait-time")) && online;
	}
	
	public boolean isFull() {
		return playercount >= maxplayers;
	}
	
	
	List<ProxiedPlayer> queue = new ArrayList<>();
	public List<ProxiedPlayer> getQueue() {
		return queue;
	}
	
	/**
	 * If the player can access the server. (Bungeecord's restricted servers)
	 * @param ply The player
	 * @return if the player can join based on bungeecord's restricted servers system
	 */
	public boolean canAccess(ProxiedPlayer ply) {
		boolean ca = false;
		for(ServerInfo si : servers) {
			if(si.canAccess(ply)) {
				ca = true;
				break;
			}
		}
		return ca;
	}
	
	
	boolean whitelisted = false;
	List<String> whitelistedplayers = new ArrayList<>();
	public void setWhitelisted(boolean b) {
		whitelisted = b;
	}
	public void setWhitelistedPlayers(List<String> plys) {
		whitelistedplayers = plys;
	}
	public boolean getWhitelisted() {
		return whitelisted;
	}
	public boolean isWhitelisted() {
		return whitelisted;
	}
	public List<String> getWhitelistedPlayers() {
		return whitelistedplayers;
	}
	
	/**
	 * If the server is joinable as a player
	 * @param p The player
	 * @return If the player can join the server
	 */
	public boolean isJoinable(ProxiedPlayer p) {
		return (!whitelisted || whitelistedplayers.contains(p.getName())) &&
				this.isOnline() &&
				this.canAccess(p) &&
				!this.isFull() &&
				!this.isPaused();
				
	}
	public String getJoinableDebug(ProxiedPlayer p) {
		return "whitelist: "+(!whitelisted || whitelistedplayers.contains(p.getName())) + "\n" +
				"online: "+this.isOnline() +"\n"+
				"canaccess: "+this.canAccess(p) +"\n"+
				"full: "+ !this.isFull() +"\n"+
				"paused: "+!this.isPaused();
	}
	
	
	boolean paused = false;
	public boolean isPaused() {
		return paused;
	}
	public void setPaused(boolean to) {
		paused = to;
	}
}