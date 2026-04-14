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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * 笔记列表适配器
 *
 * 继承自 CursorAdapter，负责将数据库查询结果（Cursor）绑定到 ListView 上显示。
 * 主要功能：
 * 1. 将 Cursor 中的笔记数据转换为 NotesListItem 视图进行展示
 * 2. 管理多选模式（ActionMode）下的选中状态
 * 3. 提供获取选中项 ID 和小部件属性的方法，供批量删除等操作使用
 * 4. 自动计算并缓存当前列表中笔记的总数量
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";
    private Context mContext;
    /**
     * 存储列表项的选中状态
     * Key: 列表项的位置索引（position）
     * Value: 是否选中（true 为选中，false 为未选中）
     */
    private HashMap<Integer, Boolean> mSelectedIndex;
    private int mNotesCount;
    private boolean mChoiceMode;

    /**
     * 桌面小部件属性类
     *
     * 用于在批量操作时，识别哪些笔记关联了桌面小部件。
     * 每个桌面小部件有唯一的 widgetId 和类型 widgetType。
     */
    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    };

    /**
     * 构造函数
     *
     * @param context 上下文对象
     */
    public NotesListAdapter(Context context) {
        super(context, null);// 初始时 Cursor 为空，后续通过 changeCursor 设置
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    /**
     * 创建新的列表项视图
     *
     * 当 ListView 需要一个新的视图来显示数据时调用。
     * 这里直接返回自定义的 NotesListItem 视图。
     *
     * @param context 上下文
     * @param cursor  数据游标（未使用，因为视图创建不依赖数据）
     * @param parent  父视图
     * @return 新建的 NotesListItem 视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    /**
     * 将数据绑定到视图
     *
     * 当 ListView 需要显示某个位置的数据时调用。
     * 从 Cursor 中提取数据，封装成 NoteItemData，然后交给 NotesListItem 进行渲染。
     *
     * @param view    要绑定的视图（必须是 NotesListItem 类型）
     * @param context 上下文
     * @param cursor  数据游标，已移动到当前位置
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置指定位置项的选中状态
     *
     * @param position 列表位置索引
     * @param checked  是否选中
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    /**
     * 判断当前是否处于多选模式
     *
     * @return true 表示多选模式，false 表示普通模式
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置多选模式
     *
     * 切换模式时会清空所有已选中的项。
     *
     * @param mode true 进入多选模式，false 退出多选模式
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }
    /**
     * 全选或取消全选所有笔记项
     *
     * 注意：只会选中类型为 NOTE 的项，文件夹项不会被选中。
     *
     * @param checked true 全选，false 取消全选
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 获取所有被选中项的笔记 ID 集合
     *
     * 用于批量删除、移动等操作。
     *
     * @return 包含所有选中笔记 ID 的 HashSet
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }

        return itemSet;
    }

    /**
     * 获取所有被选中项关联的桌面小部件属性
     *
     * 当用户批量删除笔记时，需要同时删除关联的桌面小部件。
     * 此方法收集所有选中笔记中有关联小部件的信息。
     *
     * @return 包含小部件属性的 HashSet，如果没有则返回空集合
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /**
                     * Don't close cursor here, only the adapter could close it
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取当前已选中的笔记数量
     *
     * @return 选中的笔记数量
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    @Override
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /**
     * 计算当前列表中笔记项的总数量
     *
     * 遍历整个 Cursor，统计类型为 TYPE_NOTE 的项的数量。
     * 结果缓存在 mNotesCount 中，供 isAllSelected 等方法使用。
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}
