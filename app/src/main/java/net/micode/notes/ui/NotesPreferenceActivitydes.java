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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/**
 * 便签设置界面
 *
 * 继承自PreferenceActivity，提供应用的各种设置选项。
 * 主要功能包括：
 * 1. Google任务同步账号设置
 * 2. 手动触发同步/取消同步
 * 3. 显示上次同步时间
 * 4. 同步进度显示
 * 5. 便签背景随机显示设置
 */
public class NotesPreferenceActivity extends PreferenceActivity {

    // ==================== 常量定义 ====================

    // SharedPreferences文件名
    public static final String PREFERENCE_NAME = "notes_preferences";

    // 同步账号名称的存储键
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    // 上次同步时间的存储键
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    // 背景随机显示的存储键
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    // 同步账号分类的存储键
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    // 添加账号时的权限过滤器键
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    // ==================== 成员变量 ====================

    private PreferenceCategory mAccountCategory;  // 账号设置分类
    private GTaskReceiver mReceiver;               // GTask广播接收器
    private Account[] mOriAccounts;                // 原有的Google账号列表
    private boolean mHasAddedAccount;              // 是否添加了新账号

    /**
     * Activity创建时的回调方法
     * @param icicle 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 启用应用图标作为导航按钮（左上角返回箭头）
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 从XML资源加载偏好设置界面
        addPreferencesFromResource(R.xml.preferences);

        // 获取账号设置分类
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);

        // 注册GTask广播接收器，用于接收同步状态更新
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        mOriAccounts = null;

        // 添加自定义头部视图（包含同步按钮和状态信息）
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    /**
     * Activity恢复时的回调方法
     * 检查是否添加了新账号，如有则自动设置为同步账号
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 如果用户添加了新账号，自动设置为同步账号
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                // 遍历找出新增的账号
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        setSyncAccount(accountNew.name);  // 设置为同步账号
                        break;
                    }
                }
            }
        }

        refreshUI();  // 刷新界面
    }

    /**
     * Activity销毁时的回调方法
     * 注销广播接收器，防止内存泄漏
     */
    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * 加载账号偏好设置
     * 创建账号设置项，处理点击事件
     */
    private void loadAccountPreference() {
        mAccountCategory.removeAll();  // 清空原有设置项

        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);

        // 设置账号偏好的标题和摘要
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));

        // 设置点击监听器
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {  // 未在同步中
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // 首次设置账号，显示选择账号对话框
                        showSelectAccountAlertDialog();
                    } else {
                        // 已设置账号，显示修改确认对话框
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    // 同步中不能修改账号
                    Toast.makeText(NotesPreferenceActivity.this,
                                    R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 加载同步按钮
     * 根据同步状态设置按钮文字和点击行为，显示上次同步时间或同步进度
     */
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // 设置按钮状态
        if (GTaskSyncService.isSyncing()) {
            // 同步中：显示"取消同步"
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            // 未同步：显示"立即同步"
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        // 未设置账号时禁用同步按钮
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // 设置上次同步时间显示
        if (GTaskSyncService.isSyncing()) {
            // 同步中显示进度
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                // 显示上次同步时间
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 刷新整个设置界面
     */
    private void refreshUI() {
        loadAccountPreference();  // 刷新账号设置
        loadSyncButton();         // 刷新同步按钮
    }

    /**
     * 显示选择账号对话框
     * 列出所有Google账号供用户选择
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 设置自定义标题
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);  // 不使用默认按钮

        // 获取Google账号列表
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            // 创建账号名称数组
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;  // 标记当前选中的账号
                }
                items[index++] = account.name;
            }

            // 设置单选列表
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());  // 设置选择的账号
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        // 添加"添加账号"视图
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();

        // 点击"添加账号"跳转到系统添加账号界面
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                        "gmail-ls"  // 只显示Google账号类型
                });
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    /**
     * 显示修改账号确认对话框
     * 提供修改账号、移除账号、取消三个选项
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 设置自定义标题，显示当前账号
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        // 设置菜单项
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),  // 修改账号
                getString(R.string.preferences_menu_remove_account),  // 移除账号
                getString(R.string.preferences_menu_cancel)           // 取消
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();  // 修改账号
                } else if (which == 1) {
                    removeSyncAccount();              // 移除账号
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
    }

    /**
     * 获取所有Google账号
     * @return Google账号数组
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 设置同步账号
     * @param account 账号名称
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            // 保存账号到SharedPreferences
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // 清空上次同步时间
            setLastSyncTime(this, 0);

            // 清空本地GTask相关信息（在新线程中执行）
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");      // 清空GTask ID
                    values.put(NoteColumns.SYNC_ID, 0);        // 清空同步ID
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            // 显示成功提示
            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 移除同步账号
     * 清除SharedPreferences中保存的账号信息和同步时间
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // 清空本地GTask相关信息
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    /**
     * 获取同步账号名称
     * @param context 上下文
     * @return 账号名称，未设置则返回空字符串
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 设置上次同步时间
     * @param context 上下文
     * @param time 时间戳
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 获取上次同步时间
     * @param context 上下文
     * @return 上次同步时间戳，未同步过则返回0
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /**
     * GTask广播接收器
     * 接收同步服务的广播，实时更新UI显示同步状态和进度
     */
    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();  // 刷新界面

            // 如果正在同步，更新进度文本
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }
        }
    }

    /**
     * 选项菜单项点击回调
     * 处理ActionBar上的返回按钮点击
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:  // 点击左上角返回箭头
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  // 清除栈顶之上的Activity
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}