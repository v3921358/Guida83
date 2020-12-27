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

import java.util.LinkedList;

public class Equip extends Item implements IEquip {

    private byte upgradeSlots;
    private byte level;
    private short str, dex, _int, luk, hp, mp, watk, matk, wdef, mdef, acc, avoid, hands, speed, jump;
    private int hammer;
    private int ringid;
    private int sn;
    private int uniqueid;

    public Equip(int id, short position) {
        super(id, position, (short) 1);
        ringid = -1;
    }

    public Equip(int id, short position, int ringid) {
        super(id, position, (short) 1);
        this.ringid = ringid;
    }

    @Override
    public IItem copy() {
        Equip ret = new Equip(getItemId(), getPosition(), ringid);
        ret.str = str;
        ret.dex = dex;
        ret._int = _int;
        ret.luk = luk;
        ret.hp = hp;
        ret.mp = mp;
        ret.matk = matk;
        ret.mdef = mdef;
        ret.watk = watk;
        ret.wdef = wdef;
        ret.acc = acc;
        ret.avoid = avoid;
        ret.hands = hands;
        ret.speed = speed;
        ret.jump = jump;
        ret.upgradeSlots = upgradeSlots;
        ret.level = level;
        ret.hammer = hammer;
        ret.log = new LinkedList<>(log);
        ret.setOwner(getOwner());
        ret.setFlag(getFlag());
        ret.setExpiration(getExpiration());
        ret.setQuantity(getQuantity());
        ret.setStoragePosition(getStoragePosition());
        if (isByGM()) {
            ret.setGMFlag();
        }
        return ret;
    }

    @Override
    public byte getType() {
        return IItem.EQUIP;
    }

    @Override
    public byte getUpgradeSlots() {
        return upgradeSlots;
    }

    @Override
    public int getRingId() {
        return ringid;
    }

    @Override
    public short getStr() {
        return str;
    }

    @Override
    public short getDex() {
        return dex;
    }

    @Override
    public short getInt() {
        return _int;
    }

    @Override
    public short getLuk() {
        return luk;
    }

    @Override
    public short getHp() {
        return hp;
    }

    @Override
    public short getMp() {
        return mp;
    }

    @Override
    public short getWatk() {
        return watk;
    }

    @Override
    public short getMatk() {
        return matk;
    }

    @Override
    public short getWdef() {
        return wdef;
    }

    @Override
    public short getMdef() {
        return mdef;
    }

    @Override
    public short getAcc() {
        return acc;
    }

    @Override
    public short getAvoid() {
        return avoid;
    }

    @Override
    public short getHands() {
        return hands;
    }

    @Override
    public short getSpeed() {
        return speed;
    }

    @Override
    public short getJump() {
        return jump;
    }

    public void setStr(short str) {
        this.str = str > 0 ? str : 0;
    }

    public void setDex(short dex) {
        this.dex = dex > 0 ? dex : 0;
    }

    public void setInt(short _int) {
        this._int = _int > 0 ? _int : 0;
    }

    public void setLuk(short luk) {
        this.luk = luk > 0 ? luk : 0;
    }

    public void setHp(short hp) {
        this.hp = hp > 0 ? hp : 0;
    }

    public void setMp(short mp) {
        this.mp = mp > 0 ? mp : 0;
    }

    public void setWatk(short watk) {
        this.watk = watk > 0 ? watk : 0;
    }

    public void setMatk(short matk) {
        this.matk = matk > 0 ? matk : 0;
    }

    public void setWdef(short wdef) {
        this.wdef = wdef > 0 ? wdef : 0;
    }

    public void setMdef(short mdef) {
        this.mdef = mdef > 0 ? mdef : 0;
    }

    public void setAcc(short acc) {
        this.acc = acc > 0 ? acc : 0;
    }

    public void setAvoid(short avoid) {
        this.avoid = avoid > 0 ? avoid : 0;
    }

    public void setHands(short hands) {
        this.hands = hands;
    }

    public void setSpeed(short speed) {
        this.speed = speed > 0 ? speed : 0;
    }

    public void setJump(short jump) {
        this.jump = jump > 0 ? jump : 0;
    }

    public void setUpgradeSlots(byte upgradeSlots) {
        this.upgradeSlots = upgradeSlots;
    }

    @Override
    public byte getLevel() {
        return level;
    }

    public void setLevel(byte level) {
        this.level = level;
    }

    @Override
    public int getViciousHammers() {
        return hammer;
    }

    public void setViciousHammers(int hammer) {
        this.hammer = hammer;
    }

    @Override
    public int getSN() {
        return sn;
    }

    @Override
    public void setSN(int sn) {
        this.sn = sn;
    }

    @Override
    public int getUniqueId() {
        return uniqueid;
    }

    @Override
    public void setUniqueId(int id) {
        uniqueid = id;
    }

    @Override
    public void setQuantity(short quantity) {
        if (quantity < 0 || quantity > 1) {
            throw new RuntimeException("Setting the quantity to " + quantity + " on an equip (itemid: " + getItemId() + ")");
        }
        super.setQuantity(quantity);
    }

    public void setRingId(int ringId) {
        ringid = ringId;
    }
}