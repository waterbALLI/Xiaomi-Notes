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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 笔记列表项视图
 *
 * 继承自 LinearLayout，作为 ListView 中的单个条目视图。
 * 主要功能：
 * 1. 根据数据类型（笔记/文件夹/通话记录）展示不同的布局样式
 * 2. 处理多选模式下 CheckBox 的显示与选中状态
 * 3. 根据笔记在列表中的位置（第一个/最后一个/中间）设置不同的背景圆角
 * 4. 显示提醒图标（闹钟）、标题、时间、通话联系人等信息
 */
public class NotesListItem extends LinearLayout {
    /** 提醒图标（闹钟图标，表示笔记设置了提醒） */
    private ImageView mAlert;

    /** 标题文本（笔记摘要或文件夹名称） */
    private TextView mTitle;

    /** 时间文本（最后修改时间的相对时间，如"5分钟前"） */
    private TextView mTime;

    /** 通话联系人名称（仅在通话记录笔记中显示） */
    private TextView mCallName;

    /** 当前绑定的数据对象 */
    private NoteItemData mItemData;
    private CheckBox mCheckBox;

    /**
     * 构造函数
     *
     * 加载布局文件 note_item.xml 并初始化各个子视图。
     *
     * @param context 上下文对象
     */
    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 将数据绑定到视图
     *
     * 这是本类的核心方法，根据数据类型和状态决定各个子视图的可见性与内容。
     * 需要处理三种不同的展示场景：
     * 1. 通话记录文件夹（特殊的系统文件夹）
     * 2. 通话记录笔记（属于通话记录文件夹下的笔记）
     * 3. 普通笔记或文件夹
     *
     * @param context     上下文对象
     * @param data        绑定的数据对象
     * @param choiceMode  是否处于多选模式
     * @param checked     当前项是否被选中（仅在多选模式下有效）
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // ========== 处理多选模式下的复选框 ==========
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            // 多选模式下，只有笔记可以选中，文件夹不显示复选框
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;
        // ========== 场景一：通话记录文件夹 ==========
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);

            // ========== 场景二：通话记录笔记 ==========
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
                // ========== 场景三：普通笔记或文件夹 ==========
            } else {
                mAlert.setVisibility(View.GONE);
            }
        } else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            if (data.getType() == Notes.TYPE_FOLDER) {
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                                data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        setBackground(data);
    }

    /**
     * 设置列表项背景
     *
     * 小米便签的列表项是圆角卡片样式，相邻的笔记会连接在一起形成整体。
     * 此方法根据笔记在连续分组中的位置（第一个/中间/最后一个/单独一个）设置不同的背景资源。
     *
     * 背景类型说明：
     * - single: 单独一条笔记（上下都是文件夹或列表边界）
     * - first: 连续笔记组中的第一条（上方是文件夹，下方是同组笔记）
     * - last: 连续笔记组中的最后一条（上方是同组笔记，下方是文件夹）
     * - normal: 连续笔记组中的中间项（上下都是同组笔记）
     *
     * @param data 笔记数据对象
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                // 中间笔记：没有圆角，上下连接
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹：使用统一的文件夹背景
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前绑定的数据对象
     *
     * 供外部（如 NotesListActivity）在长按等操作时获取被操作项的数据。
     *
     * @return 当前绑定的 NoteItemData 对象
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}
