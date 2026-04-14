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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Google Tasks 同步管理器类
 *
 * 这是小米便签与 Google Tasks 同步功能的核心控制器类。
 * 它负责协调本地数据库和远程服务器之间的数据同步，采用单例模式设计。
 *
 * 主要职责：
 * 1. 管理整个同步流程的生命周期
 * 2. 维护本地数据与远程数据的映射关系
 * 3. 实现双向同步算法（解决新增、更新、删除、冲突）
 * 4. 处理同步过程中的各种异常和取消操作
 *

 *
 * @see GTaskClient
 * @see SqlNote
 * @see Node
 */
public class GTaskManager {

    // ==================== 常量定义 ====================

    /**
     * 同步成功状态码
     */
    public static final int STATE_SUCCESS = 0;

    /**
     * 网络错误状态码
     */
    public static final int STATE_NETWORK_ERROR = 1;

    /**
     * 内部错误状态码（数据库错误、JSON解析错误等）
     */
    public static final int STATE_INTERNAL_ERROR = 2;

    /**
     * 同步进行中状态码
     */
    public static final int STATE_SYNC_IN_PROGRESS = 3;

    /**
     * 用户取消同步状态码
     */
    public static final int STATE_SYNC_CANCELLED = 4;

    /**
     * 日志标签
     */
    private static final String TAG = GTaskManager.class.getSimpleName();

    /**
     * 单例实例
     */
    private static GTaskManager mInstance = null;

    // ==================== 成员变量 ====================

    /**
     * Activity 引用
     * 用于 AccountManager 获取 AuthToken，需要 Activity 上下文
     */
    private Activity mActivity;

    /**
     * 应用上下文
     * 用于 ContentResolver 等操作
     */
    private Context mContext;

    /**
     * 内容解析器
     * 用于数据库的增删改查操作
     */
    private ContentResolver mContentResolver;

    /**
     * 同步进行中标志
     * true 表示正在同步，防止重复执行
     */
    private boolean mSyncing;

    /**
     * 取消同步标志
     * 当用户点击取消时设置为 true，同步过程会检查此标志并提前退出
     */
    private boolean mCancelled;

    /**
     * 远程任务列表映射表
     * Key: 任务列表的 GID (Google Task ID)
     * Value: TaskList 对象
     *
     * 用于快速查找远程任务列表，只包含"MIUI:"前缀的文件夹
     */
    private HashMap<String, TaskList> mGTaskListHashMap;

    /**
     * 远程节点映射表
     * Key: 节点的 GID
     * Value: Node 对象（Task 或 TaskList）
     *
     * 包含所有需要同步的远程节点（任务列表和任务）
     */
    private HashMap<String, Node> mGTaskHashMap;

    /**
     * 元数据映射表
     * Key: 关联任务的 GID
     * Value: MetaData 对象
     *
     * 元数据记录了本地便签与远程任务的映射关系
     */
    private HashMap<String, MetaData> mMetaHashMap;

    /**
     * 元数据任务列表
     * 这是一个特殊的任务列表，用于存储所有元数据任务
     * 名称固定为 "MIUI:meta"
     */
    private TaskList mMetaList;

    /**
     * 本地已删除便签的 ID 集合
     * 同步完成后，这些便签会被物理删除
     */
    private HashSet<Long> mLocalDeleteIdMap;

    /**
     * 远程 GID 到本地 ID 的映射表
     * Key: 远程节点的 GID
     * Value: 本地便签的 ID
     */
    private HashMap<String, Long> mGidToNid;

    /**
     * 本地 ID 到远程 GID 的映射表
     * Key: 本地便签的 ID
     * Value: 远程节点的 GID
     */
    private HashMap<Long, String> mNidToGid;

    /**
     * 私有构造函数（单例模式）
     * 初始化所有映射表为空
     */
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /**
     * 获取单例实例（线程安全）
     *
     * @return GTaskManager 单例对象
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 设置 Activity 上下文
     *
     * 必须在调用 sync() 之前调用，因为 AccountManager 需要 Activity 来获取 AuthToken
     *
     * @param activity 用于获取 AuthToken 的 Activity
     */
    public synchronized void setActivityContext(Activity activity) {
        mActivity = activity;
    }

    // ==================== 主要同步方法 ====================

    /**
     * 执行同步操作
     *
     * 这是同步的入口方法，协调整个同步流程。
     *
     * 同步流程：
     * 1. 检查是否已有同步进行中
     * 2. 初始化状态和映射表
     * 3. 登录 Google Tasks
     * 4. 初始化远程任务列表结构（initGTaskList）
     * 5. 执行内容同步（syncContent）
     * 6. 返回同步结果状态
     *
     * 数据流向：
     *  本地数据库  ←→ 同步引擎  ←→ 远程服务器
     *
     * @param context 上下文
     * @param asyncTask 异步任务对象，用于发布进度
     * @return 同步结果状态码
     *         STATE_SUCCESS              - 同步成功
     *         STATE_NETWORK_ERROR        - 网络错误
     *         STATE_INTERNAL_ERROR       - 内部错误
     *         STATE_SYNC_IN_PROGRESS     - 同步已在进行中
     *         STATE_SYNC_CANCELLED       - 用户取消同步
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 检查是否已有同步进行中
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }

        // 初始化同步环境
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;

        // 清空所有映射表，准备全新的同步
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();  // 重置待更新的操作队列

            // 步骤 1: 登录 Google Tasks
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
                Log.d(TAG, "Login success");
            }

            // 步骤 2: 初始化远程任务列表结构
            // 从服务器获取所有任务列表和任务，构建内存中的数据结构
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();
            Log.d(TAG, "Initialize remote data success");

            // 步骤 3: 执行内容同步
            // 比较本地和远程数据，执行新增、更新、删除操作
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
            Log.d(TAG, "Sync content success");

        } catch (NetworkFailureException e) {
            Log.e(TAG, "Network error: " + e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, "Action error: " + e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 清理所有映射表，释放内存
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
            Log.d(TAG, "Sync finished, cleaned up");
        }

        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化远程任务列表结构
     *
     * 从 Google Tasks 服务器获取所有数据，构建内存中的数据结构。
     *

     *
     * @throws NetworkFailureException 网络错误
     * @throws ActionFailureException 协议错误
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;

        GTaskClient client = GTaskClient.getInstance();
        try {
            // 获取所有任务列表
            JSONArray jsTaskLists = client.getTaskLists();
            Log.d(TAG, "Got " + jsTaskLists.length() + " task lists from server");

            // ===== 第一步：初始化元数据列表 =====
            // 元数据列表是一个特殊的任务列表，存储本地便签与远程任务的映射关系
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 查找元数据列表（名称格式："MIUI:meta"）
                if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    Log.d(TAG, "Found meta list: " + name);
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // 加载元数据列表中的所有元数据任务
                    JSONArray jsMetas = client.getTaskList(gid);
                    Log.d(TAG, "Meta list contains " + jsMetas.length() + " meta tasks");

                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);

                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                // 建立映射：任务GID → 元数据对象
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                                Log.d(TAG, "Added meta mapping for task GID: " + metaData.getRelatedGid());
                            }
                        }
                    }
                    break;  // 找到后退出循环
                }
            }

            // 如果服务器上没有元数据列表，创建一个新的
            if (mMetaList == null) {
                Log.d(TAG, "Meta list not found, creating new one");
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
                Log.d(TAG, "Created meta list with GID: " + mMetaList.getGid());
            }

            // ===== 第二步：初始化普通任务列表 =====
            // 只处理以 "MIUI:" 开头的任务列表（即小米便签创建的）
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 跳过元数据列表，只处理以 "MIUI:" 开头的普通任务列表
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META)) {

                    Log.d(TAG, "Processing task list: " + name);
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载该任务列表中的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    Log.d(TAG, "Task list contains " + jsTasks.length() + " tasks");

                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        String taskGid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);

                        if (task.isWorthSaving()) {
                            // 关联元数据（如果存在）
                            task.setMetaInfo(mMetaHashMap.get(taskGid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(taskGid, task);
                            Log.d(TAG, "Added task: " + task.getName() + " (GID: " + taskGid + ")");
                        }
                    }
                }
            }

            Log.d(TAG, "initGTaskList completed. Lists: " + mGTaskListHashMap.size()
                    + ", Tasks: " + (mGTaskHashMap.size() - mGTaskListHashMap.size()));

        } catch (JSONException e) {
            Log.e(TAG, "JSON error: " + e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    // ==================== 内容同步方法 ====================

    /**
     * 同步内容（核心同步逻辑）
     *
     * 这是同步的核心算法，实现本地数据库和远程数据之间的双向同步。
     *
     * 同步流程：
     *- 查询回收站中的便签
     *- 标记需要删除远程对应的任务
     *

     * 步骤 2: 同步文件夹（syncFolder）
     * - 同步根文件夹和通话记录文件夹
     *- 同步用户创建的文件夹
     * - 处理文件夹的新增、更新、删除
     *
     *
     * 步骤 3: 同步便签内容
     *  - 遍历本地所有便签
     * - 与远程数据比较，决定同步类型
     * - 执行相应的同步操作
     *
     *
     *  步骤 4: 处理剩余远程节点
     * - 本地不存在的远程节点 → 添加到本地
     *
     *
     * 步骤 5: 清理和刷新
     * - 批量删除本地标记的便签
     * - 刷新本地同步ID
     *
     * @throws NetworkFailureException 网络错误
     * @throws ActionFailureException 操作错误
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // ===== 步骤 1: 处理本地已删除的便签 =====
        // 查询回收站（parent_id = ID_TRASH_FOLER）中的便签
        Log.d(TAG, "Step 1: Processing locally deleted notes");
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 本地已删除，远程还存在 → 需要删除远程节点
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                        Log.d(TAG, "Marked remote node for deletion: " + gid);
                    }
                    // 记录本地需要删除的便签ID
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
                Log.d(TAG, "Found " + mLocalDeleteIdMap.size() + " deleted notes");
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ===== 步骤 2: 同步文件夹 =====
        Log.d(TAG, "Step 2: Syncing folders");
        syncFolder();

        // ===== 步骤 3: 同步便签内容 =====
        // 查询所有非系统类型、不在回收站中的便签
        Log.d(TAG, "Step 3: Syncing note content");
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                Log.d(TAG, "Found " + c.getCount() + " notes to sync");
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);

                    if (node != null) {
                        // 本地和远程都存在
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                        Log.d(TAG, "Existing note: gid=" + gid + ", syncType=" + syncType);
                    } else {
                        // 本地存在，远程不存在
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增（没有关联的GID）
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                            Log.d(TAG, "New note to add to remote");
                        } else {
                            // 远程已删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                            Log.d(TAG, "Remote note deleted, need to delete local");
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ===== 步骤 4: 处理剩余的远程节点 =====
        // 远程存在但本地不存在的节点 → 添加到本地
        Log.d(TAG, "Step 4: Processing remaining remote nodes");
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        int addCount = 0;
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            addCount++;
        }
        Log.d(TAG, "Added " + addCount + " remote nodes to local");

        // ===== 步骤 5: 清理本地删除表 =====
        if (!mCancelled) {
            Log.d(TAG, "Step 5: Cleaning up local deleted notes");
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
            Log.d(TAG, "Deleted " + mLocalDeleteIdMap.size() + " local notes");
        }

        // ===== 步骤 6: 刷新本地同步ID =====
        if (!mCancelled) {
            Log.d(TAG, "Step 6: Refreshing local sync IDs");
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
            Log.d(TAG, "Sync IDs refreshed");
        }
    }

    /**
     * 同步文件夹
     *
     * 处理文件夹（任务列表）的同步，包括：
     * - 根文件夹（ID_ROOT_FOLDER）：对应 "Default" 任务列表
     * - 通话记录文件夹（ID_CALL_RECORD_FOLDER）：对应 "CallNote" 任务列表
     * - 用户创建的普通文件夹
     *
     * @throws NetworkFailureException 网络错误
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        Log.d(TAG, "Syncing folders...");

        // ===== 同步根文件夹 =====
        // 根文件夹对应 Google Tasks 中的 "Default" 列表
        Log.d(TAG, "Syncing root folder");
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    // 远程存在根文件夹
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 检查名称是否需要更新
                    String expectedName = GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_DEFAULT;
                    if (!node.getName().equals(expectedName)) {
                        Log.d(TAG, "Root folder name mismatch, updating remote");
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    }
                } else {
                    // 远程不存在根文件夹，需要创建
                    Log.d(TAG, "Root folder not found on remote, creating");
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ===== 同步通话记录文件夹 =====
        Log.d(TAG, "Syncing call note folder");
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] { String.valueOf(Notes.ID_CALL_RECORD_FOLDER) }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        String expectedName = GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_CALL_NOTE;
                        if (!node.getName().equals(expectedName)) {
                            Log.d(TAG, "Call note folder name mismatch, updating remote");
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                        }
                    } else {
                        Log.d(TAG, "Call note folder not found on remote, creating");
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ===== 同步用户创建的普通文件夹 =====
        Log.d(TAG, "Syncing user-created folders");
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                Log.d(TAG, "Found " + c.getCount() + " user folders to sync");
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                        Log.d(TAG, "Existing folder: name=" + node.getName()
                                + ", syncType=" + syncType);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                            Log.d(TAG, "New folder to add to remote");
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                            Log.d(TAG, "Remote folder deleted, need to delete local");
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ===== 处理远程新增的文件夹 =====
        Log.d(TAG, "Processing remote-only folders");
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        int remoteAddCount = 0;
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
                remoteAddCount++;
                Log.d(TAG, "Adding remote folder: " + node.getName());
            }
        }
        Log.d(TAG, "Added " + remoteAddCount + " remote folders to local");

        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 执行具体的同步操作
     *
     * 根据同步类型，调用相应的处理方法。
     *
     *
     * @param syncType 同步类型
     * @param node 远程节点（可能为 null）
     * @param c 数据库游标（可能为 null）
     * @throws NetworkFailureException 网络错误
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;

        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                // 远程存在，本地不存在 → 添加到本地
                Log.d(TAG, "ADD_LOCAL: " + (node != null ? node.getName() : "null"));
                addLocalNode(node);
                break;

            case Node.SYNC_ACTION_ADD_REMOTE:
                // 本地存在，远程不存在 → 添加到远程
                Log.d(TAG, "ADD_REMOTE");
                addRemoteNode(node, c);
                break;

            case Node.SYNC_ACTION_DEL_LOCAL:
                // 远程已删除，本地还存在 → 删除本地
                Log.d(TAG, "DEL_LOCAL");
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;

            case Node.SYNC_ACTION_DEL_REMOTE:
                // 本地已删除，远程还存在 → 删除远程
                Log.d(TAG, "DEL_REMOTE");
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;

            case Node.SYNC_ACTION_UPDATE_LOCAL:
                // 远程有更新 → 更新本地
                Log.d(TAG, "UPDATE_LOCAL");
                updateLocalNode(node, c);
                break;

            case Node.SYNC_ACTION_UPDATE_REMOTE:
                // 本地有更新 → 更新远程
                Log.d(TAG, "UPDATE_REMOTE");
                updateRemoteNode(node, c);
                break;

            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // 冲突：双方都有修改 → 本地优先，覆盖远程
                Log.d(TAG, "UPDATE_CONFLICT - using local version");
                updateRemoteNode(node, c);
                break;

            case Node.SYNC_ACTION_NONE:
                // 无变化
                Log.d(TAG, "NONE - no action needed");
                break;

            case Node.SYNC_ACTION_ERROR:
            default:
                Log.e(TAG, "Unknown sync action type: " + syncType);
                throw new ActionFailureException("unknown sync action type");
        }
    }

    // ==================== 同步操作方法 ====================

    /**
     * 添加本地节点（从远程同步到本地）
     *
     * 当远程存在而本地不存在的节点，调用此方法在本地创建对应的便签。
     *
     * @param node 远程节点（Task 或 TaskList）
     * @throws NetworkFailureException 网络错误
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        Log.d(TAG, "Adding local node: " + node.getName());
        SqlNote sqlNote;

        // 根据节点类型创建本地便签
        if (node instanceof TaskList) {
            // 处理任务列表 → 本地文件夹
            String nodeName = node.getName();

            if (nodeName.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                // 默认文件夹 → 根文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
                Log.d(TAG, "Mapping to root folder");
            } else if (nodeName.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                // 通话记录文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
                Log.d(TAG, "Mapping to call note folder");
            } else {
                // 普通文件夹
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
                Log.d(TAG, "Creating new folder");
            }
        } else {
            // 处理任务 → 本地便签
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();

            try {
                // 检查并处理ID冲突
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // ID已存在，移除让系统自动生成新ID
                            note.remove(NoteColumns.ID);
                            Log.d(TAG, "ID conflict resolved, using auto-generated ID");
                        }
                    }
                }

                // 检查并处理数据ID冲突
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                data.remove(DataColumns.ID);
                                Log.d(TAG, "Data ID conflict resolved");
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, "JSON error: " + e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            // 设置父文件夹ID
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
            Log.d(TAG, "Creating new note with parent: " + parentId);
        }

        // 保存到数据库
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);
        Log.d(TAG, "Saved local node with ID: " + sqlNote.getId());

        // 更新映射关系
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 更新本地节点（从远程同步更新到本地）
     *
     * 当远程节点有更新时，调用此方法更新本地对应的便签。
     *
     * @param node 远程节点
     * @param c 数据库游标，指向要更新的本地记录
     * @throws NetworkFailureException 网络错误
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        Log.d(TAG, "Updating local node: " + node.getName());

        SqlNote sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 设置父文件夹ID
        Long parentId = (node instanceof Task)
                ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);

        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);
        Log.d(TAG, "Updated local node ID: " + sqlNote.getId());

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 添加远程节点（从本地同步到远程）
     *
     * 当本地有新增便签时，调用此方法在远程创建对应的任务。
     *
     * @param node 远程节点（可能为 null）
     * @param c 数据库游标，指向本地新增的记录
     * @throws NetworkFailureException 网络错误
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        Log.d(TAG, "Adding remote node");
        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // 根据本地便签类型创建远程节点
        if (sqlNote.isNoteType()) {
            // 本地便签 → 远程任务
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            GTaskClient.getInstance().createTask(task);
            n = (Node) task;
            Log.d(TAG, "Created remote task: " + task.getName());

            // 添加元数据
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            // 本地文件夹 → 远程任务列表
            TaskList tasklist = null;

            // 构建期望的文件夹名称
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            Log.d(TAG, "Looking for folder: " + folderName);

            // 检查是否已存在同名的远程任务列表
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    Log.d(TAG, "Found existing remote folder with GID: " + gid);
                    break;
                }
            }

            // 不存在则创建新的
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
                Log.d(TAG, "Created new remote folder: " + tasklist.getName());
            }
            n = (Node) tasklist;
        }

        // 更新本地记录的 GTask ID
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
        Log.d(TAG, "Updated local note with GTask ID: " + n.getGid());

        // 更新映射关系
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 更新远程节点（从本地同步到远程）
     *
     * 当本地便签有更新时，调用此方法更新远程对应的任务。
     *
     * @param node 远程节点
     * @param c 数据库游标，指向本地更新的记录
     * @throws NetworkFailureException 网络错误
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        Log.d(TAG, "Updating remote node: " + node.getName());
        SqlNote sqlNote = new SqlNote(mContext, c);

        // 更新远程节点内容
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);
        Log.d(TAG, "Added update to queue");

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // 处理任务移动（如果父文件夹发生了变化）
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            if (preParentList != curParentList) {
                Log.d(TAG, "Moving task from list '" + preParentList.getName()
                        + "' to '" + curParentList.getName() + "'");
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // 清除本地修改标记
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
        Log.d(TAG, "Cleared local modified flag");
    }

    /**
     * 更新远程元数据
     *
     * 元数据记录了本地便签与远程任务的映射关系。
     * 每次同步后都需要更新元数据，确保下次同步能正确匹配。
     *
     * @param gid 远程任务的 GID
     * @param sqlNote 本地便签对象
     * @throws NetworkFailureException 网络错误
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            Log.d(TAG, "Updating remote meta for GID: " + gid);
            MetaData metaData = mMetaHashMap.get(gid);

            if (metaData != null) {
                // 更新已有元数据
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
                Log.d(TAG, "Updated existing meta data");
            } else {
                // 创建新元数据
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
                Log.d(TAG, "Created new meta data");
            }
        }
    }

    /**
     * 刷新本地同步ID
     *
     * 同步完成后，将远程节点的最后修改时间（last_modified）保存到本地，
     * 用于下次同步时判断数据是否有变化。
     *
     * @throws NetworkFailureException 网络错误
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        Log.d(TAG, "Refreshing local sync IDs");

        // 重新获取最新的远程数据
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();
        Log.d(TAG, "Re-initialized remote data");

        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                Log.d(TAG, "Updating sync IDs for " + c.getCount() + " notes");
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                        Log.d(TAG, "Updated sync ID for note " + c.getLong(SqlNote.ID_COLUMN)
                                + " to " + node.getLastModified());
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    // ==================== Getter 和其他方法 ====================

    /**
     * 获取当前同步账号名称
     *
     * @return 账号名称
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消正在进行的同步
     *
     * 设置取消标志，同步过程会定期检查此标志并提前退出。
     * 这个方法可以在另一个线程中调用（如 UI 线程）。
     */
    public void cancelSync() {
        mCancelled = true;
        Log.d(TAG, "Sync cancellation requested");
    }
}