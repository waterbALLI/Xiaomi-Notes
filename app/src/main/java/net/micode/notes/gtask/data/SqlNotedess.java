
package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * SQL便签操作类
 *
 * 这是便签同步功能中的核心数据类，负责管理便签（Note）及其关联数据（Data）的数据库操作。
 *
 * 数据模型说明：
 * - 一条便签（SqlNote）对应数据库中的一条 note 记录
 * - 一条便签可以包含多条数据（SqlData），对应 data 表中的多条记录
 * - 便签类型包括：普通便签（TYPE_NOTE）、文件夹（TYPE_FOLDER）、系统文件夹（TYPE_SYSTEM）
 *
 * 主要功能：
 * 1. 在本地数据库和JSON格式之间转换便签数据
 * 2. 管理便签的基本属性（标题、颜色、提醒时间等）
 * 3. 管理便签关联的Data数据（文本内容等）
 * 4. 支持乐观锁版本控制，防止同步冲突
 * 5. 支持本地修改标记，优化同步性能
 */
public class SqlNote {

    // 日志标签
    private static final String TAG = SqlNote.class.getSimpleName();

    // 无效ID常量
    private static final int INVALID_ID = -99999;

    // ==================== 数据库查询投影 ====================

    /**
     * 便签表查询字段投影
     * 定义了从note表查询时需要获取的所有列
     */
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID,                    // 便签ID
            NoteColumns.ALERTED_DATE,          // 提醒时间
            NoteColumns.BG_COLOR_ID,           // 背景颜色ID
            NoteColumns.CREATED_DATE,          // 创建时间
            NoteColumns.HAS_ATTACHMENT,        // 是否有附件
            NoteColumns.MODIFIED_DATE,         // 修改时间
            NoteColumns.NOTES_COUNT,           // 便签数量（仅文件夹使用）
            NoteColumns.PARENT_ID,             // 父文件夹ID
            NoteColumns.SNIPPET,               // 摘要（标题或预览文本）
            NoteColumns.TYPE,                  // 类型（便签/文件夹/系统文件夹）
            NoteColumns.WIDGET_ID,             // 桌面小部件ID
            NoteColumns.WIDGET_TYPE,           // 桌面小部件类型
            NoteColumns.SYNC_ID,               // 同步ID
            NoteColumns.LOCAL_MODIFIED,        // 本地修改标记
            NoteColumns.ORIGIN_PARENT_ID,      // 原始父文件夹ID（用于同步）
            NoteColumns.GTASK_ID,              // Google Tasks ID
            NoteColumns.VERSION                // 版本号（乐观锁）
    };

    // 投影中各列的索引常量
    public static final int ID_COLUMN = 0;                    // ID列索引
    public static final int ALERTED_DATE_COLUMN = 1;          // 提醒时间列索引
    public static final int BG_COLOR_ID_COLUMN = 2;           // 背景颜色列索引
    public static final int CREATED_DATE_COLUMN = 3;          // 创建时间列索引
    public static final int HAS_ATTACHMENT_COLUMN = 4;        // 附件标记列索引
    public static final int MODIFIED_DATE_COLUMN = 5;         // 修改时间列索引
    public static final int NOTES_COUNT_COLUMN = 6;           // 便签数量列索引
    public static final int PARENT_ID_COLUMN = 7;             // 父文件夹ID列索引
    public static final int SNIPPET_COLUMN = 8;               // 摘要列索引
    public static final int TYPE_COLUMN = 9;                  // 类型列索引
    public static final int WIDGET_ID_COLUMN = 10;            // 小部件ID列索引
    public static final int WIDGET_TYPE_COLUMN = 11;          // 小部件类型列索引
    public static final int SYNC_ID_COLUMN = 12;              // 同步ID列索引
    public static final int LOCAL_MODIFIED_COLUMN = 13;       // 本地修改标记列索引
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;     // 原始父文件夹ID列索引
    public static final int GTASK_ID_COLUMN = 15;             // Google Tasks ID列索引
    public static final int VERSION_COLUMN = 16;              // 版本号列索引

    // ==================== 成员变量 ====================

    private Context mContext;                    // 上下文
    private ContentResolver mContentResolver;    // 内容解析器
    private boolean mIsCreate;                   // 是否为新建便签
    private long mId;                            // 便签ID
    private long mAlertDate;                     // 提醒时间
    private int mBgColorId;                      // 背景颜色ID
    private long mCreatedDate;                   // 创建时间
    private int mHasAttachment;                  // 是否有附件（0=无，1=有）
    private long mModifiedDate;                  // 修改时间
    private long mParentId;                      // 父文件夹ID
    private String mSnippet;                     // 摘要（标题或内容预览）
    private int mType;                           // 类型（便签/文件夹/系统文件夹）
    private int mWidgetId;                       // 桌面小部件ID
    private int mWidgetType;                     // 桌面小部件类型
    private long mOriginParent;                  // 原始父文件夹ID（同步时使用）
    private long mVersion;                       // 版本号（用于乐观锁）

    private ContentValues mDiffNoteValues;       // 记录便签字段的差异
    private ArrayList<SqlData> mDataList;        // 关联的数据列表（文本内容等）

    // ==================== 构造函数 ====================

    /**
     * 构造函数 - 创建新便签
     * 用于创建一个新的空白便签（尚未保存到数据库）
     *
     * @param context 上下文环境
     */
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;                                    // 标记为新建
        mId = INVALID_ID;                                    // 初始ID无效
        mAlertDate = 0;
        mBgColorId = ResourceParser.getDefaultBgId(context); // 使用默认背景色
        mCreatedDate = System.currentTimeMillis();           // 创建时间为当前时间
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();          // 修改时间为当前时间
        mParentId = 0;                                       // 默认父文件夹为根目录
        mSnippet = "";
        mType = Notes.TYPE_NOTE;                             // 默认为普通便签类型
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;   // 无效的小部件ID
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;            // 无效的小部件类型
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();               // 初始化差异记录
        mDataList = new ArrayList<SqlData>();                // 初始化数据列表
    }

    /**
     * 构造函数 - 从游标加载便签
     * 用于从数据库查询结果中加载已存在的便签
     *
     * @param context 上下文环境
     * @param c 数据库游标，指向便签记录
     */
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;                // 标记为已存在
        loadFromCursor(c);                // 从游标加载基本属性
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)     // 如果是普通便签，加载关联数据
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    /**
     * 构造函数 - 根据ID加载便签
     * 用于从数据库加载指定ID的便签
     *
     * @param context 上下文环境
     * @param id 便签ID
     */
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);               // 根据ID加载便签
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)     // 如果是普通便签，加载关联数据
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    // ==================== 数据加载方法 ====================

    /**
     * 根据便签ID从数据库加载数据
     *
     * @param id 便签ID
     */
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            // 查询指定ID的便签
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] { String.valueOf(id) }, null);
            if (c != null) {
                c.moveToNext();
                loadFromCursor(c);
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * 从游标加载便签基本属性
     *
     * @param c 数据库游标，已经指向有效位置
     */
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
    }

    /**
     * 加载便签关联的数据内容
     * 查询data表中属于当前便签的所有数据记录
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();
        try {
            // 查询关联的数据记录
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] { String.valueOf(mId) }, null);
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");
                    return;
                }
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    // ==================== JSON转换方法 ====================

    /**
     * 从JSON对象设置便签内容
     *
     * 解析从Google Tasks服务器获取的JSON数据，填充到当前便签对象中。
     * 支持三种类型的节点：
     * - 系统文件夹（TYPE_SYSTEM）：只读，不能修改
     * - 文件夹（TYPE_FOLDER）：只更新摘要和类型
     * - 普通便签（TYPE_NOTE）：更新所有属性和关联数据
     *
     * @param js 包含便签数据的JSON对象
     * @return 是否设置成功
     */
    public boolean setContent(JSONObject js) {
        try {
            // 获取便签部分的JSON数据
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹不允许修改
                Log.w(TAG, "cannot set system folder");

            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 文件夹：只能更新摘要和类型
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                // 普通便签：更新所有属性
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                // ID
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                // 提醒时间
                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                // 背景颜色
                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                // 创建时间
                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                // 附件标记
                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                // 修改时间
                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                // 父文件夹ID
                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                // 摘要
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                // 类型
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                // 小部件ID
                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                // 小部件类型
                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                // 原始父文件夹ID
                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 处理关联的数据列表
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;

                    // 尝试查找已存在的数据对象
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }

                    // 如果不存在则创建新的
                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 获取便签内容的JSON表示
     *
     * 将当前便签对象转换为JSON格式，用于同步到Google Tasks服务器
     *
     * @return 包含便签所有数据的JSON对象，如果便签未保存则返回null
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            JSONObject note = new JSONObject();

            if (mType == Notes.TYPE_NOTE) {
                // 普通便签：包含所有属性
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                // 添加关联数据
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);

            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // 文件夹：只包含基本属性
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    // ==================== Setter方法 ====================

    /**
     * 设置父文件夹ID
     */
    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    /**
     * 设置Google Tasks ID
     */
    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    /**
     * 设置同步ID
     */
    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    /**
     * 重置本地修改标记
     * 同步完成后调用，标记该便签已与服务器同步
     */
    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    // ==================== Getter方法 ====================

    public long getId() {
        return mId;
    }

    public long getParentId() {
        return mParentId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    // ==================== 数据库提交方法 ====================

    /**
     * 提交便签到数据库
     *
     * 将当前便签对象保存到数据库：
     * - 如果是新建便签，执行插入操作
     * - 如果是已存在的便签，执行更新操作（仅更新变化的字段）
     *
     * 支持乐观锁版本验证，防止同步冲突。
     * 对于普通便签，还会递归提交关联的SqlData。
     *
     * @param validateVersion 是否验证版本号（true=验证，false=不验证）
     * @throws ActionFailureException 数据库操作失败时抛出
     */
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            // ===== 插入新便签 =====

            // 如果ID无效且差异值中包含ID，则移除（让数据库自动生成）
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            // 执行插入操作
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果是普通便签，提交关联的数据
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else {
            // ===== 更新已有便签 =====

            // 检查ID有效性
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }

            if (mDiffNoteValues.size() > 0) {
                mVersion++;  // 版本号递增
                int result = 0;

                if (!validateVersion) {
                    // 不验证版本，直接更新
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] { String.valueOf(mId) });
                } else {
                    // 验证版本：只有当版本号小于等于当前版本时才更新（乐观锁）
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                                    + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] { String.valueOf(mId), String.valueOf(mVersion) });
                }

                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            // 如果是普通便签，提交关联的数据
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 刷新本地数据（重新加载）
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        // 清空差异记录，标记为已保存
        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}