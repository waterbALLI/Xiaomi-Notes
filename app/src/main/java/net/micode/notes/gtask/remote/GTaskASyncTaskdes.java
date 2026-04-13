package net.micode.notes.gtask.remote;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * Google Tasks 异步同步任务类
 *
 * 继承自 AsyncTask，在后台线程中执行 Google Tasks 与本地便签的同步操作。
 * 同步过程中通过通知栏显示进度，同步完成后显示结果通知。
 *
 * 主要功能：
 * 1. 在后台执行同步操作（不阻塞 UI 线程）
 * 2. 通过通知栏实时显示同步进度
 * 3. 同步完成后显示成功/失败/取消等状态通知
 * 4. 支持取消正在进行的同步操作
 * 5. 同步完成后通过回调通知调用方
 *
 * 同步结果状态：
 * - STATE_SUCCESS：同步成功
 * - STATE_NETWORK_ERROR：网络错误
 * - STATE_INTERNAL_ERROR：内部错误
 * - STATE_SYNC_CANCELLED：用户取消同步
 *
 * @see AsyncTask
 * @see GTaskManager
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    // 同步通知的唯一标识 ID
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成监听器接口
     * 当同步任务完成时回调（无论成功或失败）
     */
    public interface OnCompleteListener {
        /**
         * 同步完成时的回调方法
         * 在后台线程中执行，注意不要直接操作 UI
         */
        void onComplete();
    }

    // ==================== 成员变量 ====================

    private Context mContext;                          // 上下文对象
    private NotificationManager mNotifiManager;       // 通知管理器
    private GTaskManager mTaskManager;                 // Google Tasks 同步管理器
    private OnCompleteListener mOnCompleteListener;    // 同步完成监听器

    /**
     * 构造函数
     *
     * @param context 上下文环境
     * @param listener 同步完成监听器
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        // 获取通知管理器服务
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        // 获取 GTaskManager 单例实例
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 取消正在进行的同步操作
     * 供外部调用，用于停止当前同步任务
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布进度更新
     * 封装 publishProgress 方法，供 GTaskManager 调用
     *
     * @param message 进度消息文本
     */
    public void publishProgess(String message) {
        publishProgress(new String[] { message });
    }

    /**
     * 显示通知
     *
     * 根据同步状态显示不同类型的通知：
     * - 同步中：显示进度文本
     * - 同步成功：点击跳转到便签列表
     * - 同步失败/取消：点击跳转到设置页面
     *
     * 注意：原代码中的 Notification 构造函数已过时，
     * 此处已适配为使用 Notification.Builder 的新写法。
     *
     * @param tickerId 通知标题的资源 ID（如 R.string.ticker_syncing）
     * @param content 通知内容文本
     */
    private void showNotification(int tickerId, String content) {
        // 创建 PendingIntent，设置点击通知后的跳转目标
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent;

        if (tickerId != R.string.ticker_success) {
            // 同步中或失败：点击跳转到设置页面
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), pendingIntentFlags);
        } else {
            // 同步成功：点击跳转到便签列表页面
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), pendingIntentFlags);
        }

        // 使用 Notification.Builder 构建通知（兼容新版本 Android）
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setContentTitle(mContext.getString(R.string.app_name))  // 设置标题为应用名称
                .setContentText(content)                                 // 设置通知内容
                .setSmallIcon(R.drawable.icon_app)                       // 设置小图标
                .setContentIntent(pendingIntent)                         // 设置点击跳转
                .setWhen(System.currentTimeMillis());                    // 设置通知时间

        Notification notification = builder.getNotification();

        // Android 13 (API 33) 及以上需要检查通知权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || mContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
        }
    }

    /**
     * 后台任务执行方法
     *
     * 在后台线程中执行同步操作，不阻塞 UI 线程。
     * 首先发布登录进度消息，然后调用 GTaskManager 执行实际同步。
     *
     * @param unused 无用的参数（AsyncTask 的输入参数）
     * @return 同步结果状态码
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布登录进度消息，显示当前同步账号
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));
        // 执行同步操作
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新回调方法
     *
     * 当 publishProgress 被调用时，此方法在 UI 线程中执行。
     * 更新通知栏的进度内容，并如果是 GTaskSyncService 上下文则发送广播。
     *
     * @param progress 进度消息数组
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        // 显示同步中的通知
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果当前上下文是 GTaskSyncService，发送广播更新 UI
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 后台任务完成后的回调方法
     *
     * 在 UI 线程中执行，根据同步结果显示相应的通知。
     * 同时通过回调通知调用方同步已完成。
     *
     * @param result 同步结果状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        // 根据结果类型显示不同通知
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            // 记录上次同步时间
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 用户取消同步
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }

        // 触发完成回调（在新线程中执行，避免阻塞 UI）
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}