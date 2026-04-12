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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 闹钟提醒Activity
 *
 * 当闹钟时间到达时，由AlarmReceiver启动该Activity，显示提醒对话框并播放闹钟铃声。
 * 该Activity具有以下特点：
 * 1. 无标题栏
 * 2. 能够在锁屏界面显示
 * 3. 屏幕关闭时可以点亮屏幕
 * 4. 播放系统默认闹钟铃声
 * 5. 显示便签的部分内容作为提醒信息
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {

    // 便签ID（从Intent的URI中解析得到）
    private long mNoteId;

    // 便签内容片段（用于对话框显示）
    private String mSnippet;

    // 便签内容预览的最大长度（超过60字符则截断并添加"..."）
    private static final int SNIPPET_PREW_MAX_LEN = 60;

    // 媒体播放器，用于播放闹钟铃声
    MediaPlayer mPlayer;

    /**
     * Activity创建时的回调方法
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 请求显示无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 获取窗口对象，设置窗口属性
        final Window win = getWindow();
        // 允许在锁屏界面显示
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕当前是关闭状态，添加屏幕唤醒相关标志
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON      // 保持屏幕常亮
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON        // 点亮屏幕
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON  // 允许屏幕锁定时显示
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);  // 布局嵌入装饰
        }

        // 获取启动该Activity的Intent
        Intent intent = getIntent();

        try {
            // 从Intent的Data URI中解析便签ID
            // URI格式：content://net.micode.notes/notes/123
            // getPathSegments()返回["notes", "123"]，索引1即为ID
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));

            // 根据便签ID获取便签内容片段
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);

            // 如果内容超过最大长度，截断并添加省略号
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ?
                    mSnippet.substring(0, SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;  // 解析失败，直接返回
        }

        // 初始化媒体播放器
        mPlayer = new MediaPlayer();

        // 检查便签是否仍然存在且类型为普通便签
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            // 显示提醒对话框
            showActionDialog();
            // 播放闹钟铃声
            playAlarmSound();
        } else {
            // 便签已被删除，直接关闭Activity
            finish();
        }
    }

    /**
     * 判断屏幕是否亮起
     * @return true=屏幕亮起，false=屏幕关闭
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放闹钟铃声
     *
     * 获取系统默认的闹钟铃声URI，设置音频流类型，循环播放
     * 需要注意静音模式对闹钟流的影响
     */
    private void playAlarmSound() {
        // 获取系统默认的闹钟铃声URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取受静音模式影响的音频流设置
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 如果闹钟流受静音模式影响，则使用受影响的流类型，否则使用闹钟流
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }

        try {
            // 设置媒体数据源
            mPlayer.setDataSource(this, url);
            // 准备播放
            mPlayer.prepare();
            // 设置为循环播放
            mPlayer.setLooping(true);
            // 开始播放
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒对话框
     *
     * 对话框显示便签内容片段，提供关闭和进入便签两个按钮
     * - 确定按钮：关闭对话框（停止闹钟）
     * - 取消按钮（仅屏幕亮起时显示）：进入便签编辑界面
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);  // 设置标题为应用名称
        dialog.setMessage(mSnippet);          // 设置显示内容为便签片段
        dialog.setPositiveButton(R.string.notealert_ok, this);  // 确定按钮，点击关闭
        // 仅在屏幕亮起时显示"进入便签"按钮
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);  // 进入便签按钮
        }
        // 显示对话框并设置关闭监听器
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击回调
     * @param dialog 触发回调的对话框
     * @param which  被点击的按钮
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:  // 点击了"进入便签"按钮
                // 创建Intent，启动NoteEditActivity查看便签详情
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);  // 传递便签ID
                startActivity(intent);
                break;
            default:
                // 点击确定按钮或其他情况，不做额外处理，对话框关闭时会停止闹钟
                break;
        }
    }

    /**
     * 对话框关闭时的回调
     * 停止闹钟铃声并关闭Activity
     * @param dialog 被关闭的对话框
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();  // 停止播放铃声
        finish();          // 关闭Activity
    }

    /**
     * 停止闹钟铃声并释放资源
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();     // 停止播放
            mPlayer.release();  // 释放MediaPlayer资源
            mPlayer = null;
        }
    }
}