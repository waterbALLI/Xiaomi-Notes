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

package net.micode.notes.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 *
 * 该广播接收器通常在设备开机时触发，负责重新注册所有未过期的便签闹钟。
 * 因为系统重启后，之前通过AlarmManager设置的闹钟会被清除，所以需要重新注册。
 *
 * 工作流程：
 * 1. 查询数据库中所有提醒时间大于当前时间的便签
 * 2. 为每个便签创建一个PendingIntent（关联AlarmReceiver）
 * 3. 通过AlarmManager重新设置闹钟
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 数据库查询投影：只需要便签ID和提醒时间两个字段
    private static final String [] PROJECTION = new String [] {
            NoteColumns.ID,           // 便签ID
            NoteColumns.ALERTED_DATE  // 提醒时间
    };

    // 列索引常量（对应PROJECTION数组的顺序）
    private static final int COLUMN_ID                = 0;  // ID列的索引
    private static final int COLUMN_ALERTED_DATE      = 1;  // 提醒时间列的索引

    /**
     * 接收到广播时的回调方法
     *
     * 通常在系统启动完成时（BOOT_COMPLETED）触发，重新设置所有未过期的闹钟
     *
     * @param context 上下文环境
     * @param intent  触发广播的Intent对象
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间（毫秒）
        long currentDate = System.currentTimeMillis();

        // 查询数据库中所有提醒时间大于当前时间且类型为普通便签的记录
        // 注意：这里只查询未过期的闹钟（提醒时间 > 当前时间）
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },  // 替换占位符 "?" 为当前时间
                null);  // 不排序

        // 遍历查询结果，为每个未过期的便签重新设置闹钟
        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    // 获取便签的提醒时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // 创建Intent，指定闹钟触发时启动AlarmReceiver
                    Intent sender = new Intent(context, AlarmReceiver.class);

                    // 为Intent设置Data（URI包含便签ID）
                    // 这样AlarmReceiver可以通过getData()获取具体是哪个便签的闹钟
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                            c.getLong(COLUMN_ID)));

                    // 创建PendingIntent
                    // 参数: context, requestCode(0), intent, flags(0)
                    // PendingIntent用于延迟执行，在闹钟时间到达时发送广播
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    // 获取AlarmManager系统服务
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    // 设置闹钟
                    // 参数1: RTC_WAKEUP - 使用系统绝对时间，并在设备休眠时唤醒CPU
                    // 参数2: alertDate - 闹钟触发的时间（毫秒）
                    // 参数3: pendingIntent - 触发时执行的PendingIntent
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext());  // 继续处理下一条记录
            }
            c.close();  // 关闭游标，释放资源
        }
    }
}