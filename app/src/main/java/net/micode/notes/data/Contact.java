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
    // 简单内存缓存：key 为号码，value 为联系人名称，避免重复查询通讯录。
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";

    // 通过 PHONE_NUMBERS_EQUAL + min_match 做号码归一化匹配，提升不同格式号码的命中率。
    // 其中 '+' 是占位符，运行时会被 toCallerIDMinMatch(phoneNumber) 替换。
    // 这体现了早期 Android 工程师为了在硬件性能有限的情况下，实现“秒出”联系人姓名所做的底层优化。
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    // 根据电话号码查询联系人名称：先查缓存，未命中再查系统通讯录。
    //无论你创建了多少个联系人对象，内存里永远只有这唯一一个 sContactCache。
    //所有的地方查询联系人时，都共用这一个缓存字典。
    //sContactCache 的设计体现了对性能的考虑，避免频繁访问 ContentResolver 导致的卡顿，在此处就相当于一级缓存
    // 同时 CALLER_ID_SELECTION 的构造也展示了对号码匹配准确性的追求。
    public static String getContact(Context context, String phoneNumber) {
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 缓存命中直接返回，减少 ContentResolver 查询开销。
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }
        //在上述操作不能在缓存中找到数据，我们就需要以下的操作来从内存中查询数据了。
        // 将占位符 '+' 替换为当前号码的 min-match 值，拼接最终 selection 条件。
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);
//在这里cursor 是一个查询结果的集合，包含了满足条件的联系人信息。我们通过 moveToFirst() 方法来判断是否有查询结果
// ，如果有，我们就可以从 cursor 中获取联系人名称，
// 并将其缓存起来，并非是直接返回结果,以便下次查询时直接返回。最后，无论查询成功与否，我们都要关闭 cursor 以释放资源，避免内存泄漏。
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                // 查询成功后写入缓存，后续同号码可直接读取。
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 兜底：列索引异常时记录错误并返回 null，避免崩溃。
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 无论成功失败都关闭游标，防止资源泄漏。
                cursor.close();
            }
        } else {
            // 未匹配到联系人时输出调试日志，便于排查号码匹配问题。
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
