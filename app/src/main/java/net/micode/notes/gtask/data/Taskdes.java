package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google Tasks 任务类
 *
 * 继承自 Node 抽象类，代表 Google Tasks 中的一个具体任务。
 * 任务是同步功能中的核心数据单元，对应便签应用中的一条便签。
 *
 * 主要功能：
 * 1. 维护任务的基本属性（名称、完成状态、备注、删除标记等）
 * 2. 维护任务的树形结构（父任务列表、前兄弟任务）
 * 3. 生成用于同步的 JSON 数据（创建操作、更新操作）
 * 4. 解析从服务器接收的 JSON 数据
 * 5. 在本地数据格式和远程数据格式之间转换
 * 6. 判断同步操作类型（新增、更新、删除、冲突等）
 *
 * 任务在 Google Tasks 中的组织结构：
 * - TaskList（任务列表）包含多个 Task
 * - Task 之间可以有兄弟顺序（通过 prior_sibling_id）
 * - Task 可以嵌套（通过 parent_id）
 */
public class Task extends Node {

    // 日志标签
    private static final String TAG = Task.class.getSimpleName();

    // ==================== 成员变量 ====================

    private boolean mCompleted;      // 任务是否已完成
    private String mNotes;           // 任务的备注内容
    private JSONObject mMetaInfo;    // 元数据信息（存储本地便签的关联信息）
    private Task mPriorSibling;      // 前一个兄弟任务（用于维护任务顺序）
    private TaskList mParent;        // 父任务列表（该任务属于哪个任务列表）

    /**
     * 构造函数
     * 初始化一个空的任务对象
     */
    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    // ==================== 创建和更新操作 ====================

    /**
     * 获取创建操作对应的 JSON 对象
     *
     * 生成一个符合 Google Tasks API 格式的 JSON 对象，
     * 用于向服务器发送创建任务的请求。
     *
     * @param actionId 操作ID，用于追踪请求和响应
     * @return 包含创建任务所需数据的 JSON 对象
     * @throws ActionFailureException JSON 构建失败时抛出
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 操作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务在父列表中的索引位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 实体数据（任务本身的内容）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());           // 任务名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");       // 创建者ID
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);                   // 实体类型：任务

            // 如果有备注，添加备注字段
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 父任务ID
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // 目标父类型：分组
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // 列表ID（与父任务ID相同）
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 前兄弟任务ID（用于确定任务在列表中的顺序）
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 获取更新操作对应的 JSON 对象
     *
     * 生成一个符合 Google Tasks API 格式的 JSON 对象，
     * 用于向服务器发送更新任务的请求。
     *
     * @param actionId 操作ID，用于追踪请求和响应
     * @return 包含更新任务所需数据的 JSON 对象
     * @throws ActionFailureException JSON 构建失败时抛出
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 操作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体数据（更新的内容）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());           // 任务名称

            // 如果有备注，添加备注字段
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }

            // 删除标记
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    // ==================== JSON 数据设置方法 ====================

    /**
     * 从远程 JSON 数据设置任务内容
     *
     * 解析从 Google Tasks 服务器接收的 JSON 数据，
     * 提取任务的基本属性并设置到当前对象中。
     *
     * @param js 远程服务器返回的 JSON 对象
     * @throws ActionFailureException JSON 解析失败时抛出
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 任务ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 任务名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // 备注内容
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // 删除标记
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // 完成状态
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 从本地 JSON 数据设置任务内容
     *
     * 解析本地存储的便签 JSON 数据，提取任务名称。
     * 主要用于从本地数据库恢复任务信息。
     *
     * @param js 本地存储的 JSON 对象（包含便签元数据）
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            // 获取便签部分和数据部分
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            // 验证类型是否为普通便签
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 遍历数据数组，找到文本内容作为任务名称
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 从本地内容获取 JSON 对象
     *
     * 将当前任务对象转换为本地便签格式的 JSON，
     * 用于保存到本地数据库。
     *
     * @return 表示当前任务的 JSON 对象，如果任务为空则返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 情况1：从 Web 新建的任务（没有元数据）
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();

                // 将任务名称作为便签的文本内容
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);

                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 情况2：已同步的任务（有元数据）
                // 从元数据中提取原有的便签结构
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                // 更新文本内容为当前任务名称
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 设置元数据信息
     *
     * 从 MetaData 对象中提取便签的元数据，
     * 用于在本地便签和远程任务之间建立映射。
     *
     * @param metaData 元数据对象
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    // ==================== 同步操作判断 ====================

    /**
     * 获取同步操作类型
     *
     * 根据本地数据库中的数据和远程任务的状态，
     * 判断应该执行哪种同步操作。
     *
     * 同步逻辑：
     * 1. 检查元数据是否存在
     * 2. 验证本地便签ID是否匹配
     * 3. 根据本地修改标记和同步ID判断操作类型
     *
     * @param c 数据库游标，指向本地便签记录
     * @return 同步操作类型（SYNC_ACTION_* 常量）
     */
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            // 元数据不存在，需要将远程任务推送到本地
            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            // 远程便签ID不存在，需要从本地更新到远程
            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 验证本地便签ID是否匹配
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地没有修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 双方都没有修改
                    return SYNC_ACTION_NONE;
                } else {
                    // 远程有修改，需要更新本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地有修改
                // 验证 GTask ID 是否匹配
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 只有本地修改，需要更新远程
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 双方都有修改，产生冲突
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断任务是否值得保存
     *
     * 任务值得保存的条件：
     * 1. 有元数据（已同步过的任务）
     * 2. 或任务名称非空
     * 3. 或备注内容非空
     *
     * @return true 表示需要保存，false 表示可以忽略
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // ==================== Getter 和 Setter 方法 ====================

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    /**
     * 设置前兄弟任务
     *
     * 用于维护任务在父列表中的顺序关系
     *
     * @param priorSibling 前一个兄弟任务
     */
    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    /**
     * 设置父任务列表
     *
     * @param parent 父任务列表对象
     */
    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }
}