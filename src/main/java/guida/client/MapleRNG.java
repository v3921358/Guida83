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

package guida.client;

import guida.tools.Randomizer;
import guida.tools.data.output.MaplePacketLittleEndianWriter;

/**
 * @author Xterminator
 */
public class MapleRNG {

    private long m_seed1;
    private long m_seed2;
    private long m_seed3;

    public MapleRNG() {
        final long seed = 1170746341 * Randomizer.nextInt() - 755606699;
        reset(seed, seed, seed);
    }

    private void reset(long seed1, long seed2, long seed3) {
        m_seed1 = (seed1 | 0x100000) & 0xFFFFFFFFL;
        m_seed2 = (seed2 | 0x1000) & 0xFFFFFFFFL;
        m_seed3 = (seed3 | 0x10) & 0xFFFFFFFFL;
    }

    private long next() {
        m_seed1 = (m_seed1 & 0xFFFFFFFEL) << 12 & 0xFFFFFFFFL ^ (m_seed1 << 13 & 0xFFFFFFFFL ^ m_seed1) >> 19;
        m_seed2 = (m_seed2 & 0xFFFFFFF8L) << 4 & 0xFFFFFFFFL ^ (m_seed2 << 2 & 0xFFFFFFFFL ^ m_seed2) >> 25;
        m_seed3 = (m_seed3 & 0xFFFFFFF0L) << 17 & 0xFFFFFFFFL ^ (m_seed3 << 3 & 0xFFFFFFFFL ^ m_seed3) >> 11;
        return m_seed1 ^ m_seed2 ^ m_seed3;
    }

    public void seedRNG(MaplePacketLittleEndianWriter mplew) {
        final long s1 = next();
        final long s2 = next();
        final long s3 = next();
        reset(s1, s2, s3);

        mplew.writeInt((int) s1);
        mplew.writeInt((int) s2);
        mplew.writeInt((int) s3);
    }
}