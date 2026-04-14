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
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;


//这里由于使用了静态引入，所以直接使用 NoteColumns、DataColumns 等类中的常量，无需再加前缀。

import java.util.ArrayList;

/*
================================================================================
文件注释说明
================================================================================

文件路径: app/src/main/java/net/micode/notes/model/Note.java
文件类型: Java (数据模型层)
创建时间: 2010-2011 (MiCode)
最后修改: 2026-04-14

功能描述:
本文件定义了底层的“便签”实体（Model）。
它主要用于在内存中暂存一条便签的属性变更（如修改时间、夹带文本、通话信息等），
并在适当的时机将这些变化打包，通过 ContentResolver 批量写入或更新到底层数据库中。

架构设计与设计模式:
1. **增量更新 (Delta Update)**: 通过 `ContentValues` (如 `mNoteDiffValues` 和 `mTextDataValues`) 来记录变动。修改任何属性都不会立刻写入数据库，而是等调用 `syncNote()` 时一次性提交这批变动。这极大地降低了数据库 I/O 频率，提高了界面响应速度。
2. **表结构映射**: 该类实际上是数据库中两张表的抽象映射：
   - `mNoteDiffValues` 对应的主表（`NOTE` 表），存放如颜色、文件夹ID、修改时间等元数据。
   - 内部类 `NoteData` 对应的副表（`DATA` 表），存放富文本、清单列表、通话记录等实际内容。
3. **支持事务批量提交**: 数据持久化时使用了 `ContentProviderOperation`，能将多条副表的数据更新指令打包成一个批处理操作(`applyBatch`)，通过事务一次性执行，避免了数据写入过程中的状态割裂。

================================================================================
*/
public class Note {
    // 专门用来暂存对便签元数据（Note表）的修改记录（例如修改了背景色、类型、更新时间等）。
    // 这相当于一个“修改记录单”，只存放发生变化的字段。
    private ContentValues mNoteDiffValues;
    
    // 用于暂存对便签附属内容（Data表）的修改记录（例如修改了文字内容、通话号码等）。
    // 分离的设计有助于管理主从表的数据关系。
    private NoteData mNoteData;
    private static final String TAG = "Note";

    /**
     * 向数据库中插入一条新的空便签，获取其新生成的 ID 并返回。
     * 
     * 这是一个同步(synchronized)方法，保证高并发时不会产生幻读或插入冲突。
     * 当新建便签时，应用会首先调用此方法在本地数据库生成一条占位数据，之后任何编辑
     * 都会基于这个返回的 ID 进行“更新”操作，而非一直在“插入”操作。
     * 
     * @param context 上下文对象，用于获取 ContentResolver
     * @param folderId 指定该便签所属的文件夹 ID
     * @return 返回新创建便签的数据库自增主键 ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);     // 标记为本地已修改，等待将来可能会有的云同步
        values.put(NoteColumns.PARENT_ID, folderId);   // 指定它属于哪个文件夹
        // 往便签表中插入占位数据
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1)); // 从返回的URI中提取出具体的数字ID
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 记录便签基础外壳属性的修改（例如修改背景色、放入文件夹等）。
     * 
     * 每次调用此方法不仅会将被修改的字段暂存到备忘录中，
     * 还会自动更新当前便签的“最后修改时间（MODIFIED_DATE）”，
     * 并为其打上“已被本地修改（LOCAL_MODIFIED=1）”的标记，为将来的云端同步合并提供依据。
     *
     * @param key 要修改的数据库列名 (如 NoteColumns.BG_COLOR_ID)
     * @param value 修改后的值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 记录便签中文本内容的修改。
     * 将修改委托给内部的 NoteData 进行“副表”的修改登记。
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 检查当前便签的内存数据相比数据库是否发生了变更。
     * @return true 表示有未保存的脏数据（无论是外壳元数据改变还是内部文字改变）；
     *         false 表示当前内容与数据库完全一致。
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 核心保存逻辑：将内存中的所有增量修改打包，统一刷入底层 SQLite 数据库。
     *
     * 步骤如下：
     * 1. 检查是否有未保存的改动，如果没有，直接返回 true。
     * 2. 将针对便签主表（Note）的修改 `mNoteDiffValues` 调用 update 进行更新。
     * 3. 更新成功后清理 `mNoteDiffValues`（表示该批次改动已处理完成）。
     * 4. 调用内部类 `mNoteData` 的方法，去处理便签副表（Data）的更新或插入逻辑。
     *
     * @param context 上下文
     * @param noteId 当前操作的便签 ID
     * @return true 表示保存成功，false 表示保存失败
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 如果连修改都没有，直接返回成功，不做无用的数据库消耗
        if (!isLocalModified()) {
            return true;
        }

        /**
         * In theory, once data changed...
         * 首先：更新便签本体表（Note）
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through
        }
        mNoteDiffValues.clear(); // 保存完即清空修改备忘录

        // 其次：如果附属内容（如文字）也被改了，去更新 Data 表
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 内部类：单独用来管理和缓存便签的内容数据（Data表中的记录）。
     * 便签的内容不仅包含常见的纯文本内容（TextData），还可以包含因未接来电而创建的电话关联内容（CallData）。
     */
    private class NoteData {
        private long mTextDataId;

        // 文本数据修改备忘录，用于记录正文内容的修改
        private ContentValues mTextDataValues;

        private long mCallDataId;

        // 电话数据修改备忘录，用于记录电话号码、通话日期等修改
        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 记录便签长文本数据的变更。
         * 同时由于这是属于便签整体的一部分修改，因此还要联动更新外层便签的最后修改时间和修改标记。
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 极为精妙的高性能持久化思路：事务合并。
         *
         * 当应用尝试保存文字内容（TextData）又或者同时保存电话信息（CallData）时，
         * 不是调用多次 `context.getContentResolver().insert/update()` 去造成阻塞。
         * 而是组装一个 `ContentProviderOperation` 的任务大列表，
         * 最后通过 `applyBatch` 发送给 ContentProvider 实现原子性的事务批处理。
         *
         * 此方法包含的逻辑：
         * 1. 如果该便签是全新的（没有关联的 TextDataId 或 CallDataId），就先执行 insert 插入新的关联文本行；
         * 2. 如果原来就有内容了，就使用 newUpdate 创建更新操作；
         * 3. 将前面包装好的多条操作一起发向 ContentResolver 执行。
         *
         * @param context 上下文
         * @param noteId 该数据依附的主便签 ID
         * @return Uri 操作成功返回表示该便签的统一资源标识符，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 如果原本没有内容（即新便签），加入“插入(insert)”任务
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 否则，加入“更新(update)”任务
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear(); // 清理文本修改备忘录
            }

            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            if (operationList.size() > 0) {
                try {
                    // 执行构建好的操作任务列表（批量处理），防止写数据库时多次产生IO拥堵
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}
