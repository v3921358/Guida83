var MapleLifeFactory = Java.type("guida.server.life.MapleLifeFactory");
var MaplePacketCreator = Java.type("guida.tools.MaplePacketCreator");
var Point = Java.type("java.awt.Point");

function enter(pi) {
	if(pi.isQuestStarted(21201)) { // Second Job
		for (var i = 108000700; i < 108000709; i++){
			if(pi.getPlayerCount(i) > 0 && pi.getPlayerCount(i + 10) > 0)
				continue;
			
			pi.playPortalSound();
			pi.warp(i, "out00");
			pi.getPlayer().updateQuestInfo(21202, "0");
			//pi.getPlayer().announce(MaplePacketCreator.questProgress(21203, "21203"));
			return true;
		}
		pi.message("The mirror is blank due to all the players currently recalling their memories. Please wait and try again.");
		return false;
	} else if(pi.isQuestStarted(21302)) { // Third Job
		if(pi.getPlayerCount(108010701) > 0 && pi.getPlayerCount(108010702) > 0) {
			pi.message("The mirror is blank due to all the players currently recalling their memories. Please wait and try again.");
			return false;
		} else {
			var map = pi.getClient().getChannelServer().getMapFactory().getMap(108010702);
			spawnMob(-210, 454, 9001013, map);
			
			pi.playPortalSound();
			pi.getPlayer().updateQuestInfo(21203, "1");
			pi.warp(108010701, "out00");
			return true;
		}
	} else {
		return false;
	}
}

function spawnMob(x, y, id, map) {
	if(map.getMonsterById(id) != null)
		return;
		
	var mob = MapleLifeFactory.getMonster(id);
	map.spawnMonsterOnGroudBelow(mob, new Point(x, y));
}