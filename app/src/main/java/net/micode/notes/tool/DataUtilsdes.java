
package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 数据工具类
 *
 * 提供便签应用中各种数据库操作的实用方法。
 * 包含批量删除、批量移动、查询统计、数据验证等功能。
 *
 * 主要功能：
 * 1. 批量删除便签（支持事务）
 * 2. 批量移动便签到指定文件夹
 * 3. 统计用户创建的文件夹数量
 * 4. 验证便签/数据是否存在
 * 5. 检查文件夹名称是否重复
 * 6. 获取文件夹关联的小部件信息
 * 7. 获取通话记录相关信息
 * 8. 获取便签摘要内容
 *
 * @see ContentProviderOperation
 * @see Notes
 */
public class DataUtils {

    /**
     * 日志标签，用于 Logcat 输出时标识来源
     */
    public static final String TAG = "DataUtils";

    /**
     * 批量删除便签
     *
     * 使用 ContentProviderOperation 批量删除指定 ID 的便签。
     * 支持事务操作，要么全部删除成功，要么全部失败。
     *
     * 注意：
     * - 系统根文件夹（ID_ROOT_FOLDER）不会被删除
     * - 如果 ids 为 null 或空，直接返回 true
     *
     * 工作流程：
     * 1. 遍历所有要删除的 ID
     * 2. 跳过系统根文件夹
     * 3. 为每个 ID 创建删除操作
     * 4. 批量应用操作到 ContentProvider
     * 5. 检查操作结果
     *
     * @param resolver ContentResolver 对象，用于访问数据库
     * @param ids 要删除的便签 ID 集合
     * @return true 删除成功，false 删除失败
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        // 检查 ID 集合是否为 null
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        // 检查 ID 集合是否为空
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        // 创建操作列表
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

        for (long id : ids) {
            // 保护系统根文件夹，防止误删
            if (id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            // 创建删除操作
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }

        try {
            // 批量执行删除操作
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);

            // 验证操作结果
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 移动便签到指定文件夹
     *
     * 将单个便签移动到目标文件夹，并记录原始文件夹位置。
     *
     * @param resolver ContentResolver 对象
     * @param id 要移动的便签 ID
     * @param srcFolderId 源文件夹 ID
     * @param desFolderId 目标文件夹 ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id,
                                       long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);      // 设置新父文件夹
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId); // 记录原始父文件夹
        values.put(NoteColumns.LOCAL_MODIFIED, 1);           // 标记为本地修改
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                values, null, null);
    }

    /**
     * 批量移动便签到指定文件夹
     *
     * 将多个便签批量移动到目标文件夹。
     * 使用 ContentProviderOperation 支持事务操作。
     *
     * @param resolver ContentResolver 对象
     * @param ids 要移动的便签 ID 集合
     * @param folderId 目标文件夹 ID
     * @return true 移动成功，false 移动失败
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
                                            long folderId) {
        // 检查 ID 集合是否为 null
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        // 创建操作列表
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

        for (long id : ids) {
            // 创建更新操作
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);      // 设置新父文件夹
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);        // 标记为本地修改
            operationList.add(builder.build());
        }

        try {
            // 批量执行更新操作
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);

            // 验证操作结果
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户创建的文件夹数量
     *
     * 统计除系统文件夹外的用户文件夹数量。
     * 不包括：
     * - 系统文件夹（TYPE_SYSTEM）
     * - 回收站（ID_TRASH_FOLER）
     *
     * @param resolver ContentResolver 对象
     * @return 用户文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        // 查询统计数量
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },  // 只查询数量
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),      // 只查询文件夹类型
                        String.valueOf(Notes.ID_TRASH_FOLER)    // 排除回收站
                },
                null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);  // 获取统计结果
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查便签是否在数据库中可见
     *
     * 验证指定类型和 ID 的便签是否存在且不在回收站中。
     *
     * @param resolver ContentResolver 对象
     * @param noteId 便签 ID
     * @param type 便签类型（TYPE_NOTE 或 TYPE_FOLDER）
     * @return true 表示存在且可见，false 表示不存在或已删除
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String[] { String.valueOf(type) },
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查便签是否在数据库中存在
     *
     * 验证指定 ID 的便签是否存在（不区分类型和位置）。
     *
     * @param resolver ContentResolver 对象
     * @param noteId 便签 ID
     * @return true 表示存在，false 表示不存在
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查数据是否在数据表中存在
     *
     * 验证指定 ID 的数据记录是否存在。
     * 数据表存储便签的详细内容（如文本、通话记录等）。
     *
     * @param resolver ContentResolver 对象
     * @param dataId 数据 ID
     * @return true 表示存在，false 表示不存在
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查文件夹名称是否可见（是否重复）
     *
     * 验证用户创建的文件夹中是否存在同名文件夹。
     *
     * @param resolver ContentResolver 对象
     * @param name 文件夹名称
     * @return true 表示名称已存在，false 表示可以使用
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        // 查询是否存在同名且不在回收站中的文件夹
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取文件夹关联的小部件属性集合
     *
     * 查询指定文件夹下所有便签关联的桌面小部件信息。
     *
     * @param resolver ContentResolver 对象
     * @param folderId 文件夹 ID
     * @return 小部件属性集合，可能为 null
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver,
                                                                  long folderId) {
        // 查询小部件信息
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);      // 小部件 ID
                        widget.widgetType = c.getInt(1);    // 小部件类型
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据便签 ID 获取通话号码
     *
     * 查询通话记录便签关联的电话号码。
     *
     * @param resolver ContentResolver 对象
     * @param noteId 便签 ID
     * @return 电话号码字符串，未找到返回空字符串
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        // 查询通话记录数据
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String[] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话时间获取便签 ID
     *
     * 查找匹配指定电话号码和通话时间的通话记录便签。
     * 使用自定义 SQL 函数 PHONE_NUMBERS_EQUAL 进行电话号码比较。
     *
     * @param resolver ContentResolver 对象
     * @param phoneNumber 电话号码
     * @param callDate 通话时间
     * @return 便签 ID，未找到返回 0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver,
                                                         String phoneNumber, long callDate) {
        // 查询匹配的通话记录
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String[] {
                        String.valueOf(callDate),
                        CallNote.CONTENT_ITEM_TYPE,
                        phoneNumber
                },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签 ID 获取摘要内容
     *
     * 查询便签的摘要字段（SNIPPET）。
     * 摘要是便签内容的简短预览，用于列表显示。
     *
     * @param resolver ContentResolver 对象
     * @param noteId 便签 ID
     * @return 摘要字符串
     * @throws IllegalArgumentException 如果便签不存在
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String[] { String.valueOf(noteId) },
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化摘要内容
     *
     * 对原始摘要进行处理：
     * 1. 去除首尾空白字符
     * 2. 截取第一行内容（遇到换行符截断）
     *
     * 这样确保在列表中显示时不会出现多余的空白和换行。
     *
     * @param snippet 原始摘要
     * @return 格式化后的摘要
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim();              // 去除首尾空白
            int index = snippet.indexOf('\n');     // 查找换行符
            if (index != -1) {
                snippet = snippet.substring(0, index);  // 只取第一行
            }
        }
        return snippet;
    }
}