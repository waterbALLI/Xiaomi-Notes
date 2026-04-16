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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 *
 * 继承自 CursorAdapter，用于在"移动到文件夹"对话框中展示文件夹列表。
 * 与 NotesListAdapter 不同，此适配器只显示文件夹名称，功能相对简单。
 *
 * 主要功能：
 * 1. 查询并展示所有文件夹（仅需 ID 和名称两个字段）
 * 2. 将根文件夹的显示名称特殊处理为"父文件夹"（用户友好）
 * 3. 使用内部类 FolderListItem 作为列表项视图
 */
public class FoldersListAdapter extends CursorAdapter {

    // ==================== 数据库查询字段投影 ====================
    /**
     * 查询所需的字段列表
     * 只需要 ID 和文件夹名称（SNIPPET），比其他适配器简洁很多
     */
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET// 文件夹名称（在数据库中存储在 SNIPPET 字段）
    };

    // ==================== 字段列索引常量 ====================
    /** ID 字段在 PROJECTION 中的索引 */
    public static final int ID_COLUMN   = 0;
    /** 文件夹名称字段在 PROJECTION 中的索引 */
    public static final int NAME_COLUMN = 1;

    // ==================== 构造函数 ====================

    /**
     * 构造函数
     *
     * @param context 上下文对象
     * @param c       包含文件夹数据的游标
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    // ==================== CursorAdapter 抽象方法实现 ====================

    /**
     * 创建新的列表项视图
     *
     * @param context 上下文
     * @param cursor  数据游标（未使用）
     * @param parent  父视图
     * @return 新建的 FolderListItem 视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 将数据绑定到视图
     *
     * 从 Cursor 中提取文件夹名称，并交给 FolderListItem 进行显示。
     * 特殊处理：如果文件夹ID是根文件夹（Notes.ID_ROOT_FOLDER），
     * 则显示为"父文件夹"而非数据库中的原始名称。
     *
     * @param view    要绑定的视图（必须是 FolderListItem 类型）
     * @param context 上下文
     * @param cursor  数据游标，已移动到当前位置
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            // 获取文件夹名称，对根文件夹做特殊显示处理
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 获取指定位置的文件夹名称
     *
     * 供外部（如对话框）在点击确定时获取用户选择的文件夹名称。
     *
     * @param context  上下文对象
     * @param position 列表位置索引
     * @return 文件夹的显示名称（根文件夹返回"父文件夹"）
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    // ==================== 内部类：文件夹列表项视图 ====================

    /**
     * 文件夹列表项视图
     *
     * 继承自 LinearLayout，作为文件夹选择列表中的单个条目。
     * 布局非常简单，只包含一个显示文件夹名称的 TextView。
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        /**
         * 构造函数
         *
         * 加载布局文件 folder_list_item.xml 并初始化 TextView。
         *
         * @param context 上下文对象
         */
        public FolderListItem(Context context) {
            super(context);
            // 将 folder_list_item.xml 布局填充到当前 LinearLayout 中
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定文件夹名称到视图
         *
         * @param name 要显示的文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
