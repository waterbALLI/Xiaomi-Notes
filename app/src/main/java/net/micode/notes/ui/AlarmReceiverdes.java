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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟广播接收器
 *
 * 当系统闹钟时间到达时，系统会发送一个广播，该类负责接收该广播并启动闹钟提醒界面。
 * 通常在AlarmManager中设置定时任务，到指定时间后通过PendingIntent发送广播，
 * AlarmReceiver收到广播后启动AlarmAlertActivity来显示提醒界面。
 *
 * 注意：需要在AndroidManifest.xml中注册该BroadcastReceiver，并声明相应的IntentFilter
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 接收到广播时的回调方法
     *
     * 当闹钟时间到达时，系统调用此方法。该方法负责启动AlarmAlertActivity，
     * 显示闹钟提醒界面（如弹出对话框、播放铃声等）。
     *
     * @param context 上下文环境，用于启动Activity
     * @param intent  触发广播的Intent对象，包含闹钟相关的附加信息
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 设置Intent的目标Activity为AlarmAlertActivity
        intent.setClass(context, AlarmAlertActivity.class);

        // 添加NEW_TASK标志，因为BroadcastReceiver的context不是Activity
        // 必须使用此标志才能在非Activity上下文中启动Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 启动闹钟提醒Activity
        context.startActivity(intent);
    }
}