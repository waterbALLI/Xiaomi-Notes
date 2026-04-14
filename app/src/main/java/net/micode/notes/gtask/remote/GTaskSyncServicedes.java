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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Tasks 同步服务类
 *
 * 这是一个 Android Service 组件，负责在后台执行 Google Tasks 与本地便签的同步操作。
 * 通过服务的方式执行同步，可以避免同步操作被 Activity 生命周期影响，
 * 即使应用退到后台，同步也能继续进行。
 *
 * 主要功能：
 * 1. 在后台执行异步同步任务（GTaskASyncTask）
 * 2. 接收来自 Activity 的启动和取消同步指令
 * 3. 通过广播向 UI 组件发送同步进度和状态更新
 * 4. 同步完成后自动停止服务
 * 5. 在低内存时自动取消同步，保护系统资源
 *
 * 服务生命周期：
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                        调用方式                                  │
 * │  startService(Intent) → onCreate() → onStartCommand()           │
 * │                                                                 │
 * │  停止方式：                                                      │
 * │  - 同步完成：stopSelf()                                         │
 * │  - 用户取消：cancelSync() → 任务取消 → stopSelf()               │
 * │  - 低内存：onLowMemory() → cancelSync()                        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 通信机制：
 * ┌──────────────┐    startService    ┌──────────────────┐
 * │   Activity   │ ─────────────────→ │ GTaskSyncService │
 * │ (UI 线程)    │ ←───────────────── │ (后台线程)        │
 * └──────────────┘    Broadcast       └──────────────────┘
 *
 * @see Service
 * @see GTaskASyncTask
 * @see GTaskManager
 */
public class GTaskSyncService extends Service {

    // ==================== 常量定义 ====================

    /**
     * Intent 中携带动作类型的键名
     * 用于区分是启动同步还是取消同步
     */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /**
     * 动作类型：启动同步
     */
    public final static int ACTION_START_SYNC = 0;

    /**
     * 动作类型：取消同步
     */
    public final static int ACTION_CANCEL_SYNC = 1;

    /**
     * 无效动作类型
     */
    public final static int ACTION_INVALID = 2;

    /**
     * 广播的 Action 名称
     * UI 组件可以通过注册这个广播来接收同步状态更新
     */
    public final static String GTASK_SERVICE_BROADCAST_NAME =
            "net.micode.notes.gtask.remote.gtask_sync_service";

    /**
     * 广播中携带"是否正在同步"的键名
     */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /**
     * 广播中携带"进度消息"的键名
     */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // ==================== 静态成员变量 ====================

    /**
     * 当前正在执行的同步任务实例
     * 使用静态变量，确保整个应用只有一个同步任务
     * 这样可以：
     * 1. 避免重复执行同步
     * 2. 可以从任何地方检查同步状态
     * 3. 可以从任何地方取消正在进行的同步
     */
    private static GTaskASyncTask mSyncTask = null;

    /**
     * 当前同步进度消息
     * 静态变量，用于在没有同步任务时也能获取最后一次的进度信息
     */
    private static String mSyncProgress = "";

    // ==================== 服务生命周期方法 ====================

    /**
     * 服务创建时的回调
     *
     * 初始化同步任务为 null，确保服务启动时没有残留的同步任务。
     * 注意：mSyncTask 是静态变量，可能保留上一次服务的状态，
     * 但由于同步完成时会设置为 null，且服务停止后不应有任务，
     * 这里重置是为了安全起见。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mSyncTask = null;
    }

    /**
     * 服务启动时的回调
     *
     * 每次通过 startService() 启动服务时都会调用此方法。
     * 根据 Intent 中的动作类型，执行相应的操作：
     * - ACTION_START_SYNC：启动同步
     * - ACTION_CANCEL_SYNC：取消同步
     *
     * @param intent 启动服务的 Intent，包含动作类型
     * @param flags 启动标志（START_STICKY 等）
     * @param startId 启动 ID，用于区分多次启动
     * @return 服务的启动模式
     *         START_STICKY：服务被杀死后会尝试重新创建
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 服务绑定时的回调
     *
     * 此服务不支持绑定（不使用 AIDL），返回 null
     *
     * @param intent 绑定请求的 Intent
     * @return null，表示不支持绑定
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 系统内存不足时的回调
     *
     * 当系统内存不足时，自动取消正在进行的同步任务，
     * 释放内存资源，避免被系统强制杀死。
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mSyncTask != null) {
            cancelSync();
        }
    }

    // ==================== 同步控制方法 ====================

    /**
     * 启动同步任务
     *
     * 如果当前没有正在执行的同步任务，则创建新的同步任务并执行。
     * 同步任务执行完成后，会自动：
     * 1. 将 mSyncTask 设置为 null
     * 2. 发送广播通知 UI 同步完成
     * 3. 停止服务（stopSelf()）
     */
    private void startSync() {
        if (mSyncTask == null) {
            // 创建同步任务，传入服务自身作为 Context
            // 同步完成后通过回调自动清理
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                @Override
                public void onComplete() {
                    // 同步完成，清理任务引用
                    mSyncTask = null;
                    // 发送完成广播
                    sendBroadcast("");
                    // 停止服务
                    stopSelf();
                }
            });

            // 发送初始广播，通知 UI 同步已开始
            sendBroadcast("");

            // 执行异步任务
            mSyncTask.execute();
        }
    }

    /**
     * 取消同步任务
     *
     * 如果存在正在执行的同步任务，调用其 cancelSync() 方法。
     * 同步任务会定期检查取消标志，并提前退出。
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // ==================== 广播发送方法 ====================

    /**
     * 发送广播通知 UI 同步状态更新
     *
     * 广播内容：
     * - 是否正在同步（mSyncTask != null）
     * - 当前进度消息
     *
     * UI 组件（如 NotesPreferenceActivity）可以注册 BroadcastReceiver
     * 来接收这些广播，并实时更新界面显示。
     *
     * @param msg 当前进度消息（如 "正在登录..."、"正在同步便签..."）
     */
    public void sendBroadcast(String msg) {
        // 更新静态进度变量，供外部查询
        mSyncProgress = msg;

        // 创建广播 Intent
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);

        // 发送广播
        sendBroadcast(intent);
    }

    // ==================== 静态工具方法 ====================

    /**
     * 启动同步（供外部调用）
     *
     * 这是启动同步的推荐方式，外部组件（如 Activity）调用此方法即可启动同步。
     *
     * 工作流程：
     * 1. 设置 GTaskManager 的 Activity 上下文（用于 AccountManager）
     * 2. 创建 Intent 指向 GTaskSyncService
     * 3. 设置动作类型为 ACTION_START_SYNC
     * 4. 启动服务
     *
     * @param activity 调用方的 Activity，用于 AccountManager 获取 AuthToken
     */
    public static void startSync(Activity activity) {
        // 设置 Activity 上下文，GTaskManager 需要用它来获取 AuthToken
        GTaskManager.getInstance().setActivityContext(activity);

        // 创建启动服务的 Intent
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);

        // 启动服务
        activity.startService(intent);
    }

    /**
     * 取消同步（供外部调用）
     *
     * 外部组件调用此方法可以取消正在进行的同步。
     *
     * @param context 上下文
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 检查是否正在同步（供外部调用）
     *
     * UI 组件可以调用此方法来判断当前是否有同步任务在执行，
     * 从而决定是否显示进度条或禁用同步按钮。
     *
     * @return true 表示正在同步，false 表示空闲
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前同步进度消息（供外部调用）
     *
     * UI 组件可以调用此方法获取当前的进度消息，
     * 用于在同步开始时显示初始状态。
     *
     * @return 当前进度消息字符串
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}