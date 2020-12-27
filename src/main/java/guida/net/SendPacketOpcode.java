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

public enum SendPacketOpcode implements MutableValueHolder<Integer> {

    // GENERAL
    PING,
    // LOGIN
    LOGIN_STATUS,
    REAUTHENTICATE,
    SEND_LINK,
    SERVERSTATUS,
    GENDER_SET,
    PIN_OPERATION,
    PIN_ASSIGNED,
    ALL_CHARLIST,
    SERVERLIST,
    CHARLIST,
    SERVER_IP,
    CHAR_NAME_RESPONSE,
    DELETE_CHAR_RESPONSE,
    ADD_NEW_CHAR_ENTRY,
    CHANNEL_SELECTED,
    RELOG_RESPONSE,
    RECOMMENDED_SERVER,
    RECOMMENDED_SERVER_MESSAGE,
    CHAR_SELECT_PIC_RESPONSE,
    LOGIN_METHOD,
    // CHANNEL
    CHANGE_CHANNEL,
    MODIFY_INVENTORY_ITEM,
    UPDATE_INVENTORY_SLOTS,
    UPDATE_STATS,
    GIVE_BUFF,
    CANCEL_BUFF,
    SET_STATS,
    RESET_STATS,
    UPDATE_SKILLS,
    FAME_RESPONSE,
    SHOW_STATUS_INFO,
    SHOW_NOTES,
    TELEPORT_ROCK,
    ALERT_GM,
    ALERT_GM_STATUS,
    UPDATE_MOUNT,
    SHOW_QUEST_COMPLETION,
    USE_SKILL_BOOK,
    SLOT_MERGE_COMPLETE,
    SORT_ITEM_COMPLETE,
    REPORT_PLAYER_MSG,
    CHAR_GENDER,
    BBS_OPERATION,
    CHAR_INFO,
    PARTY_OPERATION,
    BUDDYLIST,
    GUILD_OPERATION,
    ALLIANCE_OPERATION,
    SPAWN_PORTAL,
    SERVERMESSAGE,
    MINERVA_RESULT,
    MAPLE_TIP,
    PLAYER_NPC,
    MONSTERBOOK_ADD,
    MONSTERBOOK_CHANGE_COVER,
    MULUNGENERGY,
    FAMILY_PEDIGREE,
    FAMILY,
    FAMILY_ADD_STATUS,
    FAMILY_MEMBER_LEVELUP,
    AVATAR_MEGA,
    GM_POLICE,
    TOP_MSG,
    SKILL_MACRO,
    WARP_TO_MAP,
    MTS_OPEN,
    CS_OPEN,
    BLOCK_PORTAL,
    GENERAL_ERROR_MESSAGES,
    SHOW_EQUIP_EFFECT,
    MULTICHAT,
    WHISPER,
    SPOUSE_CHAT,
    BOSS_ENV,
    MAP_EFFECT,
    GM,
    OX_QUIZ,
    GMEVENT_INSTRUCTIONS,
    CLOCK,
    BOAT_EFFECT,
    BOAT_DOCK,
    BOX_MSG,
    REMOVE_CLOCK,
    ARIANT_SCOREBOARD,
    SPAWN_PLAYER,
    REMOVE_PLAYER_FROM_MAP,
    CHATTEXT,
    CHALKBOARD,
    UPDATE_CHAR_BOX,
    SHOW_SCROLL_EFFECT,
    SPAWN_PET,
    MOVE_PET,
    PET_CHAT,
    PET_NAMECHANGE,
    PET_COMMAND,
    SPAWN_SPECIAL_MAPOBJECT,
    REMOVE_SPECIAL_MAPOBJECT,
    MOVE_SUMMON,
    SUMMON_ATTACK,
    DAMAGE_SUMMON,
    SUMMON_SKILL,
    MOVE_PLAYER,
    CLOSE_RANGE_ATTACK,
    RANGED_ATTACK,
    MAGIC_ATTACK,
    SKILL_EFFECT,
    CANCEL_SKILL_EFFECT,
    DAMAGE_PLAYER,
    FACIAL_EXPRESSION,
    SHOW_ITEM_EFFECT,
    SHOW_CHAIR,
    UPDATE_CHAR_LOOK,
    SHOW_FOREIGN_EFFECT,
    GIVE_FOREIGN_BUFF,
    CANCEL_FOREIGN_BUFF,
    UPDATE_PARTYMEMBER_HP,
    CHAR_GUILD_NAME,
    CHAR_GUILD_INFO,
    CANCEL_CHAIR,
    SHOW_ITEM_GAIN_INCHAT,
    DOJO_WARP_UP,
    UPDATE_QUEST_INFO,
    PLAYER_HINT,
    ARAN_COMBO_COUNTER,
    ARAN_SKILL_INFO,
    HIDE_UI,
    LOCK_WINDOWS,
    TUTOR_ENABLE,
    TUTOR_ACTIONS,
    COOLDOWN,
    SPAWN_MONSTER,
    REMOVE_MONSTER,
    SPAWN_MONSTER_CONTROL,
    MOVE_MONSTER,
    MOVE_MONSTER_RESPONSE,
    APPLY_MONSTER_STATUS,
    CANCEL_MONSTER_STATUS,
    DAMAGE_MONSTER,
    MONSTER_EFFECT,
    MONSTER_HEAL,
    UNKNOWN_STATE,
    SHOW_MONSTER_HP,
    SHOW_MAGNET,
    CATCH_MONSTER,
    SPAWN_NPC,
    REMOVE_NPC,
    SPAWN_NPC_REQUEST_CONTROLLER,
    NPC_ACTION,
    NPC_SHOWHIDE,
    NPC_ANIMATION,
    SPAWN_HIRED_MERCHANT,
    DESTROY_HIRED_MERCHANT,
    UPDATE_HIRED_MERCHANT,
    DROP_ITEM_FROM_MAPOBJECT,
    REMOVE_ITEM_FROM_MAP,
    SPAWN_MIST,
    REMOVE_MIST,
    SPAWN_DOOR,
    REMOVE_DOOR,
    REACTOR_HIT,
    REACTOR_SPAWN,
    REACTOR_DESTROY,
    ROLL_SNOWBALL,
    HIT_SNOWBALL,
    SNOWBALL_MESSAGE,
    LEFT_KNOCK_BACK,
    MONSTER_CARNIVAL_START,
    MONSTER_CARNIVAL_OBTAINED_CP,
    MONSTER_CARNIVAL_PARTY_CP,
    MONSTER_CARNIVAL_SUMMON,
    MONSTER_CARNIVAL_DIED,
    ARIANT_PQ_START,
    ZAKUM_SHRINE,
    NPC_TALK,
    OPEN_NPC_SHOP,
    CONFIRM_SHOP_TRANSACTION,
    OPEN_STORAGE,
    MESSENGER,
    PLAYER_INTERACTION,
    DUEY,
    CS_UPDATE,
    CS_OPERATION,
    CS_OPERATION_COMPLETED,
    KEYMAP,
    AUTO_HP_POT,
    AUTO_MP_POT,
    SEND_TV,
    REMOVE_TV,
    ENABLE_TV,
    MTS_OPERATION2,
    MTS_OPERATION,
    VICIOUS_HAMMER;

    static {
        try {
            ExternalCodeTableGetter.populateValues(getDefaultProperties(), values());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sendops", e);
        }
    }

    private int code = -2;

    public static Properties getDefaultProperties() throws IOException {
        final Properties props = new Properties();
        final FileInputStream fileInputStream = new FileInputStream(System.getProperty("guida.sendops"));
        props.load(fileInputStream);
        fileInputStream.close();
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