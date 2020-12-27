/*
 * This file is part of Guida.
 * Copyright (C) 2020 Guida
 *
 * Guida is a fork of the OdinMS MapleStory Server.
 * The following is the original copyright notice:
 *
 *     This file is part of the OdinMS Maple Story Server
 *     Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 *                        Matthias Butz <matze@odinms.de>
 *                        Jan Christian Meyer <vimes@odinms.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation. You may not use, modify
 * or distribute this program under any other version of the
 * GNU Affero General Public License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package guida.net.channel.handler;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.server.MapleStatEffect;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Anujan
 */
public class PassiveEnergyHandler extends AbstractDealDamageHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player.getEnergyBar() >= 10000) {
            final AttackInfo attack = parseDamage(player, slea, false);
            final MapleStatEffect skillEffect = attack.getAttackEffect(player);
            if (player.getMp() < skillEffect.getMpCon() || attack.numAttacked > skillEffect.getMobCount() || attack.numDamage > skillEffect.getAttackCount()) {
                return;
            }
            int attackCount = attack.numDamage == 1 ? 1 : skillEffect.getAttackCount();
            int maxdamage = (int) (1.0 * player.calculateMaxBaseDamage(player.getTotalWatk() + 20) * skillEffect.getDamage() / 100.0 * attackCount * 1.3); // Energy Charge gives an extra 20 watk
            applyAttack(attack, player, maxdamage, attackCount);
        }
    }
}