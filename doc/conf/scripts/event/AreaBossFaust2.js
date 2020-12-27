/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/**
-- Odin JavaScript --------------------------------------------------------------------------------
	Faust2 Spawner
-- Edited by --------------------------------------------------------------------------------------
	ThreeStep (based on xQuasar's King Clang spawner)

**/

var MaplePacketCreator = Java.type("guida.tools.MaplePacketCreator");
var MapleLifeFactory = Java.type("guida.server.life.MapleLifeFactory");
var Point = Java.type("java.awt.Point");

function init() {
    scheduleNew();
}

function scheduleNew() {
    setupTask = em.schedule("start", 0);
}

function cancelSchedule() {
    if (setupTask != null)
        setupTask.cancel(true);
}

function start() {
    var theForestOfEvil2 = em.getChannelServer().getMapFactory().getMap(100040106);
    var faust2 = MapleLifeFactory.getMonster(5220002);
	
	if(theForestOfEvil2.getMonsterById(5220002) != null) {
		em.schedule("start", 3 * 60 *60 * 1000);
		return;
	}
	
    theForestOfEvil2.spawnMonsterOnGroundBelow(faust2, new Point(474, 278));
    theForestOfEvil2.broadcastMessage(MaplePacketCreator.serverNotice(6, "Faust appeared amidst the blue fog."));
	em.schedule("start", 3 * 60 *60 * 1000);
}