package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SQL数据操作类
 *
 * 负责管理便签中附加数据（如文本内容、附件等）的数据库操作。
 * 在便签的数据模型中，一条便签（Note）可以包含多条数据（Data），
 * 例如便签的文本内容就是一种Data记录。
 *
 * 该类主要功能：
 * 1. 在本地数据库和JSON格式之间转换数据
 * 2. 跟踪数据字段的修改（通过ContentValues记录差异）
 * 3. 执行数据库的插入和更新操作
 * 4. 支持乐观锁版本控制，防止并发修改冲突
 */
public class SqlData {

    // 日志标签
    private static final String TAG = SqlData.class.getSimpleName();

    // 无效ID常量，用于表示新创建的尚未保存的数据
    private static final int INVALID_ID = -99999;

    // ==================== 数据库查询投影 ====================

    /**
     * 数据表查询字段投影
     * 定义了从data表查询时需要获取的列
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID,           // 数据ID
            DataColumns.MIME_TYPE,    // MIME类型（标识数据类型，如文本/图片等）
            DataColumns.CONTENT,      // 数据内容
            DataColumns.DATA1,        // 扩展字段1（通常存储数值类型数据）
            DataColumns.DATA3         // 扩展字段3（通常存储字符串类型数据）
    };

    // 投影中各列的索引常量
    public static final int DATA_ID_COLUMN = 0;               // ID列索引
    public static final int DATA_MIME_TYPE_COLUMN = 1;        // MIME类型列索引
    public static final int DATA_CONTENT_COLUMN = 2;          // 内容列索引
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;   // DATA1列索引
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;   // DATA3列索引

    // ==================== 成员变量 ====================

    private ContentResolver mContentResolver;  // 内容解析器，用于数据库操作
    private boolean mIsCreate;                 // 是否为新建数据（true=未保存，false=已存在）
    private long mDataId;                      // 数据ID
    private String mDataMimeType;              // 数据MIME类型
    private String mDataContent;               // 数据内容
    private long mDataContentData1;            // 扩展数据1
    private String mDataContentData3;          // 扩展数据3
    private ContentValues mDiffDataValues;     // 记录差异的字段（仅包含需要更新的字段）

    /**
     * 构造函数 - 创建新数据
     * 用于创建一条新的数据记录（尚未保存到数据库）
     *
     * @param context 上下文环境
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;                     // 标记为新建
        mDataId = INVALID_ID;                 // 初始ID无效
        mDataMimeType = DataConstants.NOTE;   // 默认MIME类型为NOTE
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues(); // 初始化差异值容器
    }

    /**
     * 构造函数 - 从游标加载数据
     * 用于从数据库查询结果中加载已存在的数据记录
     *
     * @param context 上下文环境
     * @param c 数据库游标，指向要加载的数据记录
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;    // 标记为已存在的数据
        loadFromCursor(c);    // 从游标加载数据
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从数据库游标加载数据到成员变量
     *
     * @param c 数据库游标，必须已经移动到有效位置
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 从JSON对象设置数据内容
     *
     * 解析JSON对象，将各个字段的值设置到当前对象中。
     * 同时记录哪些字段发生了变化（存入mDiffDataValues），
     * 以便后续commit时只更新变化的字段。
     *
     * @param js 包含数据字段的JSON对象
     * @throws JSONException JSON解析异常
     */
    public void setContent(JSONObject js) throws JSONException {
        // 处理ID字段
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 处理MIME类型字段
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 处理内容字段
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 处理DATA1字段（通常用于存储数值，如颜色值、标志位等）
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 处理DATA3字段（通常用于存储扩展字符串）
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 获取数据内容的JSON表示
     *
     * 将当前对象的数据转换为JSON格式，用于同步或传输
     *
     * @return 包含所有数据字段的JSON对象
     * @throws JSONException JSON构建异常
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 提交数据到数据库
     *
     * 将当前数据对象保存到数据库：
     * - 如果是新数据（mIsCreate=true），执行插入操作
     * - 如果是已存在的数据，执行更新操作（仅更新变化的字段）
     *
     * 支持乐观锁版本验证，防止并发修改冲突
     *
     * @param noteId 所属便签的ID
     * @param validateVersion 是否验证版本号（true=验证，false=不验证）
     * @param version 期望的便签版本号（当validateVersion=true时使用）
     * @throws ActionFailureException 数据库操作失败时抛出
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            // ===== 插入新数据 =====
            // 如果ID无效且差异值中包含ID字段，则移除（让数据库自动生成ID）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 设置所属便签ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);

            // 执行插入操作
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 从返回的URI中解析出新生成的ID
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // ===== 更新已有数据 =====
            if (mDiffDataValues.size() > 0) {
                int result = 0;

                if (!validateVersion) {
                    // 不验证版本，直接更新
                    result = mContentResolver.update(
                            ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, mDataId),
                            mDiffDataValues, null, null);
                } else {
                    // 验证版本：只有当便签的版本号与期望版本号一致时才更新
                    // 这是乐观锁机制，防止同步过程中用户修改了便签导致冲突
                    result = mContentResolver.update(
                            ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, mDataId),
                            mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)",
                            new String[] { String.valueOf(noteId), String.valueOf(version) });
                }

                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清空差异记录，标记为已保存
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取数据ID
     *
     * @return 数据ID，如果是新建数据且未保存则返回INVALID_ID
     */
    public long getId() {
        return mDataId;
    }
}