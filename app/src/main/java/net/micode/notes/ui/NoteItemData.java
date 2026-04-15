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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记列表项数据模型
 *
 * 封装了从数据库 Cursor 中提取的笔记/文件夹数据，并提供位置状态判断。
 * 主要功能：
 * 1. 定义数据库查询所需的字段投影（PROJECTION）
 * 2. 从 Cursor 中解析各字段数据并存储
 * 3. 判断当前项在列表中的位置（第一个/最后一个/单独一个等）
 * 4. 判断当前项与前后项的关系（用于背景圆角连接效果）
 * 5. 特殊处理通话记录笔记的联系人信息
 */
public class NoteItemData {
    // ==================== 数据库查询字段投影 ====================
    /**
     * 数据库查询所需的字段列表
     * 这些字段对应 NoteColumns 中的列名，用于 ContentResolver 查询
     */
    static final String [] PROJECTION = new String [] {
        NoteColumns.ID,// 笔记/文件夹ID
        NoteColumns.ALERTED_DATE,// 提醒时间
        NoteColumns.BG_COLOR_ID,// 背景颜色ID
        NoteColumns.CREATED_DATE, // 创建时间
        NoteColumns.HAS_ATTACHMENT,// 是否有附件
        NoteColumns.MODIFIED_DATE,// 最后修改时间
        NoteColumns.NOTES_COUNT,// 文件夹内的笔记数量
        NoteColumns.PARENT_ID,// 父文件夹ID
        NoteColumns.SNIPPET,// 内容摘要
        NoteColumns.TYPE,// 类型（笔记/文件夹/系统文件夹）
        NoteColumns.WIDGET_ID,// 关联的桌面小部件ID
        NoteColumns.WIDGET_TYPE,// 桌面小部件类型
    };
// ==================== 字段列索引常量 ====================
    /** ID 字段在 PROJECTION 中的索引 */
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;
    private long mParentId;
    private String mSnippet;
    private int mType;
    private int mWidgetId;
    private int mWidgetType;
    private String mName;
    private String mPhoneNumber;

    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;
    private boolean mIsMultiNotesFollowingFolder;

    // ==================== 构造函数 ====================

    /**
     * 构造函数
     *
     * 从 Cursor 的当前行提取数据，初始化所有字段。
     * 同时会处理通话记录笔记的特殊逻辑（获取联系人信息），
     * 并调用 checkPostion() 计算当前项的位置状态。
     *
     * @param context 上下文对象，用于查询联系人
     * @param cursor  数据库游标，必须已移动到目标位置
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 从 Cursor 中读取各字段数据
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        // 去除摘要中的勾选框标记（这些标记用于编辑界面，列表中不需要显示）
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        // 处理通话记录笔记的特殊逻辑：获取联系人信息
        mPhoneNumber = "";
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            // 根据笔记ID查询关联的电话号码
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                // 根据电话号码查询联系人名称
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;// 查不到联系人则显示号码
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        // 计算当前项在列表中的位置状态
        checkPostion(cursor);
    }

    // ==================== 位置状态计算方法 ====================

    /**
     * 计算当前项在列表中的位置状态
     *
     * 通过 Cursor 判断当前项是否是第一个/最后一个/唯一项，
     * 以及是否紧跟在文件夹后面（用于决定背景圆角的连接效果）。
     *
     * @param cursor 数据库游标，当前位置即为本项
     */
    private void checkPostion(Cursor cursor) {
        // 基础位置判断
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 只有笔记类型且不是第一项时，才需要判断是否紧跟在文件夹后面
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // ==================== 位置状态访问方法 ====================

    /** @return 是否是紧跟在文件夹后面的单独一条笔记 */
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }
    /** @return 是否是紧跟在文件夹后面的多条笔记中的第一条 */
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        return mIsLastItem;
    }

    public String getCallName() {
        return mName;
    }

    public boolean isFirst() {
        return mIsFirstItem;
    }

    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId () {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    // ==================== 状态判断方法 ====================

    /** @return 是否设置了提醒（提醒时间大于0） */
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /** @return 是否是通话记录笔记 */
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    // ==================== 静态工具方法 ====================

    /**
     * 从 Cursor 中直接获取笔记类型（静态方法，无需创建实例）
     *
     * @param cursor 数据库游标，当前位置为目标项
     * @return 笔记类型
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}
