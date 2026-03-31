/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

public class Contact {
    // 静态全局联系人缓存：键为电话号码，值为联系人名称/信息，全局复用避免重复查询数据库
    private static HashMap<String, String> sContactCache;
    // 日志TAG：用于打印日志，标识该日志来自联系人模块
    private static final String TAG = "Contact";

    /**
     * 来电号码匹配查询条件（SQL Where子句）
     * 作用：从系统联系人数据库中，精准匹配【与来电号码一致】的联系人数据
     * 基于Android系统联系人ContactsContract数据库表结构查询
     */

    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            // 1. 号码匹配：判断数据库中的号码 与 传入的来电号码(?) 相等（PhoneNumberUtils匹配规则）
            // 2. 数据类型筛选：只查询【电话类型】的联系人数据，排除邮箱、地址等其他数据
            // 3. 关联子查询：只保留phone_lookup表中匹配的原始联系人ID，优化查询效率
            + "(SELECT raw_contact_id "      // 子查询：查询匹配的原始联系人ID
            + " FROM phone_lookup"           // 系统联系人优化表，专门用于号码快速查询
            + " WHERE min_match = '+')";    // 匹配国际号码格式（以+开头的号码）

    public static String getContact(Context context, String phoneNumber) {
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
