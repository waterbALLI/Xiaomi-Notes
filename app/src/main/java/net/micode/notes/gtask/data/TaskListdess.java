package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Google Tasks 任务列表类
 *
 * 继承自 Node 抽象类，代表 Google Tasks 中的一个任务列表（也称为任务分组）。
 * 任务列表是任务的容器，对应于便签应用中的文件夹（Folder）。
 *
 * 主要功能：
 * 1. 管理任务列表的基本属性（名称、索引位置等）
 * 2. 维护任务列表中的子任务集合
 * 3. 提供子任务的增删改查和移动操作
 * 4. 生成用于同步的 JSON 数据
 * 5. 解析从服务器接收的 JSON 数据
 * 6. 在本地文件夹和远程任务列表之间转换
 *
 * 数据模型关系：
 * - TaskList（任务列表）包含多个 Task（任务）
 * - TaskList 对应于本地的文件夹（Folder）
 * - 系统文件夹（如默认文件夹、通话记录文件夹）会被特殊处理
 */
public class TaskList extends Node {

    // 日志标签
    private static final String TAG = TaskList.class.getSimpleName();

    // ==================== 成员变量 ====================

    private int mIndex;                    // 任务列表在用户列表中的索引位置
    private ArrayList<Task> mChildren;    // 子任务集合（该列表中的所有任务）

    /**
     * 构造函数
     * 初始化一个空的任务列表对象
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;    // 默认索引为1
    }

    // ==================== 创建和更新操作 ====================

    /**
     * 获取创建操作对应的 JSON 对象
     *
     * 生成一个符合 Google Tasks API 格式的 JSON 对象，
     * 用于向服务器发送创建任务列表的请求。
     *
     * @param actionId 操作ID，用于追踪请求和响应
     * @return 包含创建任务列表所需数据的 JSON 对象
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

            // 列表索引位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // 实体数据（任务列表本身的内容）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());           // 列表名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");       // 创建者ID
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);                  // 实体类型：分组
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 获取更新操作对应的 JSON 对象
     *
     * 生成一个符合 Google Tasks API 格式的 JSON 对象，
     * 用于向服务器发送更新任务列表的请求。
     *
     * @param actionId 操作ID，用于追踪请求和响应
     * @return 包含更新任务列表所需数据的 JSON 对象
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

            // 任务列表ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体数据（更新的内容）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());           // 列表名称
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());    // 删除标记
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    // ==================== JSON 数据设置方法 ====================

    /**
     * 从远程 JSON 数据设置任务列表内容
     *
     * 解析从 Google Tasks 服务器接收的 JSON 数据，
     * 提取任务列表的基本属性并设置到当前对象中。
     *
     * @param js 远程服务器返回的 JSON 对象
     * @throws ActionFailureException JSON 解析失败时抛出
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 任务列表ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 列表名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 从本地 JSON 数据设置任务列表内容
     *
     * 解析本地存储的文件夹 JSON 数据，转换为任务列表的名称。
     * 支持普通文件夹和系统文件夹的转换。
     *
     * 转换规则：
     * - 普通文件夹：名称加上 MIUI 前缀
     * - 默认系统文件夹（ID_ROOT_FOLDER）：转换为 "Default"
     * - 通话记录文件夹（ID_CALL_RECORD_FOLDER）：转换为 "CallNote"
     *
     * @param js 本地存储的 JSON 对象（包含文件夹元数据）
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 普通文件夹：获取摘要作为名称，并添加 MIUI 前缀
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹：特殊处理
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    // 默认文件夹
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    // 通话记录文件夹
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 从本地内容获取 JSON 对象
     *
     * 将当前任务列表对象转换为本地文件夹格式的 JSON，
     * 用于保存到本地数据库。
     *
     * 转换规则：
     * - 移除 MIUI 前缀得到实际的文件夹名称
     * - 默认文件夹和通话记录文件夹标记为系统类型
     * - 其他文件夹标记为普通文件夹类型
     *
     * @return 表示当前任务列表的 JSON 对象
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            // 移除 MIUI 前缀
            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());

            folder.put(NoteColumns.SNIPPET, folderName);

            // 判断是否为系统文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);    // 系统文件夹
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);    // 普通文件夹

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    // ==================== 同步操作判断 ====================

    /**
     * 获取同步操作类型
     *
     * 根据本地数据库中的数据和远程任务列表的状态，
     * 判断应该执行哪种同步操作。
     *
     * 与 Task 类的逻辑略有不同：
     * - 对于文件夹冲突，直接采用本地修改（避免复杂的冲突处理）
     *
     * @param c 数据库游标，指向本地文件夹记录
     * @return 同步操作类型（SYNC_ACTION_* 常量）
     */
    public int getSyncAction(Cursor c) {
        try {
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
                    // 对于文件夹冲突，直接采用本地修改（覆盖远程）
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    // ==================== 子任务管理方法 ====================

    /**
     * 获取子任务数量
     *
     * @return 当前任务列表中的任务数量
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 添加子任务（追加到末尾）
     *
     * @param task 要添加的任务
     * @return true 添加成功，false 添加失败（task为null或已存在）
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前兄弟任务（列表中的上一个任务）
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                // 设置父任务列表
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定位置添加子任务
     *
     * @param task 要添加的任务
     * @param index 插入位置索引
     * @return true 添加成功，false 添加失败
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新任务顺序关系
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 移除子任务
     *
     * @param task 要移除的任务
     * @return true 移除成功，false 移除失败
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 清空前兄弟和父引用
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新剩余任务的顺序关系
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 移动子任务到新位置
     *
     * @param task 要移动的任务
     * @param index 目标位置索引
     * @return true 移动成功，false 移动失败
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;

        // 通过先移除后添加实现移动
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据 GID 查找子任务
     *
     * @param gid Google Tasks 任务ID
     * @return 找到的任务对象，未找到返回 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取子任务的索引位置
     *
     * @param task 要查找的任务
     * @return 任务在列表中的索引，不存在返回 -1
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 根据索引获取子任务
     *
     * @param index 索引位置
     * @return 对应索引的任务，无效索引返回 null
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 根据 GID 获取子任务
     *
     * @param gid Google Tasks 任务ID
     * @return 找到的任务对象，未找到返回 null
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 获取所有子任务列表
     *
     * @return 子任务的 ArrayList
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    // ==================== 索引管理方法 ====================

    /**
     * 设置任务列表索引
     *
     * @param index 索引值
     */
    public void setIndex(int index) {
        this.mIndex = index;
    }

    /**
     * 获取任务列表索引
     *
     * @return 当前索引值
     */
    public int getIndex() {
        return this.mIndex;
    }
}