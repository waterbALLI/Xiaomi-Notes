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

package net.micode.notes.widget;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/*
================================================================================
文件注释说明
================================================================================

文件路径: app/src/main/java/net/micode/notes/widget/NoteWidgetProvider.java
文件类型: Java (AppWidget提供者基类)
创建时间: 2010-2011 (MiCode)
最后修改: 2026-04-14

功能描述:
本文件是桌面小部件（Widget）的抽象基类，继承自 Android 原生的 AppWidgetProvider。
它处理便签在手机桌面上的小部件展示和更新逻辑，支持 2x2 和 4x4 等不同尺寸的小部件继承实现。

核心职责:
1. **数据拉取与渲染**: 查询数据库，获取小部件绑定的便签内容（Snippet）和背景颜色，然后更新桌面上的 RemoteViews（远程视图）。
2. **事件绑定**: 为桌面小部件绑定点击事件（PendingIntent），点击有内容的便签进入编辑界面，点击空白的部件则新建便签。
3. **删除联动**: 当用户从桌面上移除了该小部件时，清除数据库中对应便签的 Widget 关联 ID，防止脏数据。
4. **隐私模式**: 支持在访客/隐私模式下隐藏真实便签内容。

================================================================================
*/
public abstract class NoteWidgetProvider extends AppWidgetProvider {
    public static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.SNIPPET
    };

    public static final int COLUMN_ID           = 0;
    public static final int COLUMN_BG_COLOR_ID  = 1;
    public static final int COLUMN_SNIPPET      = 2;

    private static final String TAG = "NoteWidgetProvider";

    /**
     * 当用户从桌面上拖动小部件扔进垃圾桶（删除）时触发此回调。
     * 该方法会去数据库中，把所有绑定了这些被删小部件ID的便签记录的 WIDGET_ID 重置为 INVALID_APPWIDGET_ID。
     * 这是一种数据解绑操作。
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    /**
     * 根据桌面的 Widget ID 查询对应的便签信息。
     * 过滤条件排除了被放进回收站（ID_TRASH_FOLER）的便签，防止在桌面上显示已删除内容。
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    /**
     * 更新指定桌面小部件的显示内容（正常模式，非隐私模式）。
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 核心更新方法。负责拼装展示视图 (RemoteViews) 及设置点击跳转意图 (PendingIntent)。
     * 
     * @param context 上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds 需要更新的小部件 ID 数组
     * @param privacyMode 是否开启隐私模式（如果开启，则桌面不显示具体文字内容）
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
            boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    // 防错检查：原则上一个 Widget ID 只能对应一条便签，大于1说明数据错乱
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 查到了绑定的便签，读取片段内容和背景色
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    // 设置动作为“查看/编辑”该便签
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    // 没有查到绑定的便签（可能是刚添加空的小部件，或者是绑定的便签刚被物理删除了）
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    // 设置动作为“插入新建”便签
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                // 创建 RemoteViews 远程视图（因为桌面属于另一个进程 Launcher，必须用 RemoteViews 跨进程更新 UI）
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);
                /**
                 * Generate the pending intent to start host for the widget
                 * 为文本视图区域绑定待定意图(PendingIntent)
                 */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // 隐私模式下，隐藏原文，显示通用提示，并且点击时强制跳转到输入密码的列表页而不是直接看内容
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // 正常模式下，展示摘要片段，并绑定点击直达便签本身的 intent
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                // 将点击事件绑在 R.id.widget_text 区域上
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                // 提交更新到桌面系统
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    // ========== 抽象方法区，要求具体的子类(如2x2部件、4x4部件类)实现来提供相应资源 ==========
    
    // 获取指定颜色枚举所对应的背景图片资源ID
    protected abstract int getBgResourceId(int bgId);

    // 获取小部件的布局文件资源ID
    protected abstract int getLayoutId();

    // 获取小部件的尺寸类型（比如 TYPE_WIDGET_2X 或 TYPE_WIDGET_4X）
    protected abstract int getWidgetType();
}
