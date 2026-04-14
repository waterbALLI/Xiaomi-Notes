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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/*
================================================================================
文件注释说明
================================================================================

文件路径: app/src/main/java/net/micode/notes/model/WorkingNote.java
文件类型: Java (业务逻辑/视图模型层)
创建时间: 2010-2011 (MiCode)
最后修改: 2026-04-14

功能描述:
本文件定义了“工作便签”（WorkingNote）实体。
如果说 Note.java 是一张底层的“数据修改备忘录”，那么 WorkingNote 就是包裹在它外面的一层“智能外壳”。
它专门为便签的 UI 编辑界面 (NoteEditActivity) 提供服务，管理便签在草稿状态下的各种实时属性，
并负责与数据库进行交互（初始化加载数据、判断是否值得保存、通知桌面小部件更新等）。

架构设计与设计模式:
1. **外观模式 / 代理模式 (Facade / Proxy Wrapper)**: UI 层不需要直接去操纵底层的 Note 对象，而是通过 WorkingNote 提供的更符合直觉的 API（比如 `setBgColorId`, `setCheckListMode`）来完成操作。WorkingNote 会自动将这些动作转换到底层的 Note 实例中。
2. **生命周期管理**: 提供了 `load()` 从数据库恢复便签，和 `createEmptyNote()` 创建新便签。
3. **观察者模式 (Observer)**: 包含 `NoteSettingChangedListener`。当在代码中修改了背景色或者闹钟时，会触发回调通知 UI 重新渲染或重新注册系统闹钟。

================================================================================
*/
public class WorkingNote {
    // 底层数据修改的代理对象
    private Note mNote;
    // 当前工作便签的唯一 ID (若为 0 表示这是新建还未保存的便签)
    private long mNoteId;
    // 便签的具体文本内容缓存
    private String mContent;
    // 便签模式：0 表示普通文本，MODE_CHECK_LIST 表示清单（待办事项）模式
    private int mMode;

    // 闹钟提醒时间的 Unix 时间戳
    private long mAlertDate;
    // 最后修改时间的 Unix 时间戳
    private long mModifiedDate;
    // 背景颜色资源 ID 标识
    private int mBgColorId;
    // 如果此便签被添加到了桌面，对应的桌面小部件 ID
    private int mWidgetId;
    // 桌面小部件的类型 (2x2 还是 4x4)
    private int mWidgetType;
    // 该便签所属的文件夹 ID
    private long mFolderId;

    private Context mContext;

    private static final String TAG = "WorkingNote";

    // 软删除标记，用于判断保存时是否还要将其写入数据库
    private boolean mIsDeleted;

    // 便签属性发生变化时的回调监听器（通常由 NoteEditActivity 实现）
    private NoteSettingChangedListener mNoteSettingStatusListener;

    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    private static final int DATA_ID_COLUMN = 0;

    private static final int DATA_CONTENT_COLUMN = 1;

    private static final int DATA_MIME_TYPE_COLUMN = 2;

    private static final int DATA_MODE_COLUMN = 3;

    private static final int NOTE_PARENT_ID_COLUMN = 0;

    private static final int NOTE_ALERTED_DATE_COLUMN = 1;

    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;

    private static final int NOTE_WIDGET_ID_COLUMN = 3;

    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;

    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    // 私有构造器：用于创建一份全新的空便签
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    // 私有构造器：用于加载一份已经存在于数据库中的旧便签
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 从数据库中的 Note 表（主表）加载该便签的外壳元数据（文件夹、颜色、闹钟等）。
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        // 元数据加载完毕后，立刻开始加载附属内容
        loadNoteData();
    }

    /**
     * 从数据库中的 Data 表（副表）加载该便签的附属实际内容（如文本、电话关联记录）。
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 静态工厂方法：创建一个全新的空白便签实例。
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 静态工厂方法：从数据库中载入指定的已有便签。
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 同步当前便签的状态到数据库。
     * @return true 表示成功保存了改变，false 表示认为没有必要保存或保存失败。
     */
    public synchronized boolean saveNote() {
        // 安全拦截：检查这篇便签是否有保存的价值
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                // 如果是一篇全新的便签，就通过底层 Note.getNewNoteId 占位并获取一个全新ID
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 委托底层 Note 实例提交增量更新到数据库
            mNote.syncNote(mContext, mNoteId);

            /**
             * Update widget content if there exist any widget of this note
             * 更新桌面小部件：如果这个便签被放在了桌面上，数据修改的同时必须通知桌面小部件拉取最新数据重绘
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 核心逻辑：判断当前便签是否“值得保存”。
     * 如果用户新建了一篇便签但是一个字都没打返回了，或者打开了一篇旧便签什么都没改就返回了，
     * 这种情况下产生数据库写入是对性能的浪费，还会产生大量的空便签垃圾。
     * 
     * 不值得保存的条件：
     * 1. 已经被标记为删除了。
     * 2. 是新建的便签，而且文字内容为空的。
     * 3. 是旧的便签，但是没有任何属性或文字发生过本地修改。
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                // 通知 UI 界面背景色变了，快刷新
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 切换普通文本模式和待办事项（清单）模式
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * UI 层面文字发生改变时调用。
     * 只把改变同步在内存副本里（mContent 和 mNote），等待 saveNote 执行。
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将一篇普通便签转换为特殊的“未接来电关联便签”
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 定义一个观察者接口，专门用于沟通数据层 (WorkingNote) 和视图层 (NoteEditActivity)
     */
    public interface NoteSettingChangedListener {
        /**
         * 当前便签背景颜色改变时触发
         */
        void onBackgroundColorChanged();

        /**
         * 用户为这篇便签设置了闹钟时触发
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 从桌面小部件点进来的触发重渲染
         */
        void onWidgetChanged();

        /**
         * 在清单模式与普通模式之间切换时触发
         * @param oldMode is previous mode before change
         * @param newMode is new mode
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
