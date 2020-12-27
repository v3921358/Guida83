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

package guida.net;

import guida.tools.MutableValueHolder;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public enum RecvPacketOpcode implements MutableValueHolder<Integer> {

    // GENERIC
    PONG,
    CLIENT_ERROR,
    STRANGE_DATA,
    MAP_CHANGED,
    // LOGIN
    LOGIN_PASSWORD,
    GUEST_LOGIN,
    SERVERLIST_REREQUEST,
    CHARLIST_REQUEST,
    SERVERSTATUS_REQUEST,
    SET_GENDER,
    AFTER_LOGIN,
    REGISTER_PIN,
    SERVERLIST_REQUEST,
    TO_WORLDLIST,
    VIEW_ALL_CHAR_REQUEST,
    VIEW_ALL_CHAR_CONNECT,
    VIEW_ALL_CHAR,
    CHAR_SELECT,
    CHECK_CHAR_NAME,
    CREATE_CHAR,
    DELETE_CHAR,
    ERROR_REPORT,
    CHAR_SELECT_CREATE_PIC,
    CHAR_SELECT_PIC,
    VIEW_ALL_CHAR_CONNECT_CREATE_PIC,
    VIEW_ALL_CHAR_CONNECT_PIC,
    CLIENT_START,
    CLIENT_ON_LOGIN,
    RELOG,
    // CHANNEL
    NAME_CHANGE,
    PLAYER_LOGGEDIN,
    CHANGE_MAP,
    CHANGE_CHANNEL,
    ENTER_CASH_SHOP,
    MOVE_PLAYER,
    CANCEL_CHAIR,
    USE_CHAIR,
    CLOSE_RANGE_ATTACK,
    RANGED_ATTACK,
    MAGIC_ATTACK,
    PASSIVE_ENERGY,
    TAKE_DAMAGE,
    GENERAL_CHAT,
    CLOSE_CHALKBOARD,
    FACE_EXPRESSION,
    USE_ITEMEFFECT,
    USE_DEATH_ITEM,
    MONSTER_BOOK_COVER,
    NPC_TALK,
    REMOTE_MERCHANT,
    NPC_TALK_MORE,
    NPC_SHOP,
    STORAGE,
    HIRED_MERCHANT_REQUEST,
    DUEY_ACTION,
    MINERVA_WARP,
    SLOT_MERGE,
    ITEM_SORT,
    ITEM_MOVE,
    USE_ITEM,
    CANCEL_ITEM_EFFECT,
    TOUCHING_REACTOR,
    USE_SUMMON_BAG,
    PET_FOOD,
    USE_MOUNT_FOOD,
    USE_CASH_ITEM,
    USE_CATCH_ITEM,
    USE_SKILL_BOOK,
    USE_RETURN_SCROLL,
    USE_UPGRADE_SCROLL,
    DISTRIBUTE_AP,
    AUTO_DISTRIBUTE_AP,
    HEAL_OVER_TIME,
    DISTRIBUTE_SP,
    SPECIAL_MOVE,
    CANCEL_BUFF,
    SKILL_EFFECT,
    MESO_DROP,
    GIVE_FAME,
    CHAR_INFO_REQUEST,
    SPAWN_PET,
    CANCEL_DEBUFF,
    CHANGE_MAP_SPECIAL,
    USE_INNER_PORTAL,
    TELEPORT_ROCK,
    ALERT_GM,
    QUEST_ACTION,
    EFFECT_ON_OFF,
    THROW_BOMB,
    SKILL_MACRO,
    PINKBOX,
    MAKER,
    TREASURE_CHEST,
    REMOTE_GACHAPON,
    MULTI_CHAT,
    WHISPER,
    SPOUSE_CHAT,
    MESSENGER,
    PLAYER_INTERACTION,
    PARTY_OPERATION,
    DENY_PARTY_REQUEST,
    GUILD_OPERATION,
    DENY_GUILD_REQUEST,
    BUDDYLIST_MODIFY,
    NOTE_ACTION,
    USE_DOOR,
    CHANGE_KEYMAP,
    RING_ACTION,
    VIEW_FAMILY_PEDIGREE,
    VIEW_FAMILY,
    FAMILY_ADD,
    HACK,
    ALLIANCE_OPERATION,
    BBS_OPERATION,
    ENTER_MTS,
    UNKNOWN_STATE_RESPONSE,
    SOLOMON,
    NEW_YEARS_CARD,
    MAPLEMAS_POTION,
    CYGNUS_TUTOR_CHAT,
    ARAN_COMBO_COUNTER,
    UNKNOWN_HEADER,
    MOVE_PET,
    PET_CHAT,
    PET_COMMAND,
    PET_LOOT,
    PET_AUTO_POT,
    PET_ITEM_IGNORE,
    MOVE_SUMMON,
    SUMMON_ATTACK,
    DAMAGE_SUMMON,
    KEYMAP_OPEN,
    MOVE_LIFE,
    AUTO_AGGRO,
    MOB_DAMAGE_MOB,
    MONSTER_BOMB,
    HYPNOTIZE,
    NPC_ACTION,
    ITEM_PICKUP,
    DAMAGE_REACTOR,
    SNOWBALL,
    MONSTER_CARNIVAL,
    OBJECT_REQUEST,
    PARTY_SEARCH_REGISTER,
    PARTY_SEARCH_START,
    PARTY_ACTION,
    MAPLETV,
    TOUCHING_CS,
    CASH_SHOP,
    COUPON_CODE,
    MTS_OP,
    VICIOUS_HAMMER,
    EIGHT_EFF;

    static {
        try {
            ExternalCodeTableGetter.populateValues(getDefaultProperties(), values());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load recvops", e);
        }
    }

    private int code = -2;

    public static Properties getDefaultProperties() throws IOException {
        final Properties props = new Properties();
        final FileInputStream fis = new FileInputStream(System.getProperty("guida.recvops"));
        props.load(fis);
        fis.close();
        return props;
    }

    public void setValue(Integer code) {
        this.code = code;
    }

    @Override
    public Integer getValue() {
        return code;
    }
}