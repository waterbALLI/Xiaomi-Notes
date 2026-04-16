package net.micode.notes.tool;

/**
 * Google Tasks 字符串常量工具类
 *
 * 本类定义了与 Google Tasks 服务交互时所需的所有字符串常量。
 * 这些常量用于构建 JSON 请求、解析 JSON 响应，以及标识本地特殊数据。
 *
 * 主要用途：
 * 1. JSON 键名常量 - 用于构造和解析与 Google Tasks 服务器通信的 JSON 数据
 * 2. 操作类型常量 - 定义支持的操作类型（创建、更新、移动、获取全部）
 * 3. 实体类型常量 - 区分任务（TASK）和任务列表（GROUP）
 * 4. MIUI 便签专用常量 - 用于标识同步的文件夹和元数据
 *
 * 为什么需要这个类？
 * - 避免硬编码字符串，提高代码可维护性
 * - 集中管理字符串常量，便于修改和国际化
 * - 减少拼写错误，提高代码安全性
 * - 便于理解 Google Tasks API 的协议格式
 *
 * @see net.micode.notes.gtask.remote.GTaskClient
 * @see net.micode.notes.gtask.data.Node
 */
public class GTaskStringUtils {

    // ==================== 请求/响应 JSON 键名常量 ====================
    // 这些常量对应 Google Tasks API 中 JSON 对象的字段名

    /**
     * 操作 ID
     * 用于唯一标识一个请求操作，服务器会在响应中返回相同的 ID
     * 用于请求与响应的匹配
     */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /**
     * 操作列表
     * 包含多个操作的 JSON 数组，支持批量操作
     * 可以在一个请求中发送多个操作（如同时创建、更新多个任务）
     */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /**
     * 操作类型
     * 标识当前操作的类型（create、update、move、get_all）
     */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    /**
     * 操作类型：创建
     * 用于创建新的任务或任务列表
     */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /**
     * 操作类型：获取全部
     * 用于获取指定任务列表中的所有任务
     */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /**
     * 操作类型：移动
     * 用于将任务移动到不同的位置或不同的任务列表
     */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /**
     * 操作类型：更新
     * 用于更新现有任务或任务列表的属性（名称、完成状态、删除标记等）
     */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    /**
     * 创建者 ID
     * 标识任务或任务列表的创建者
     * 通常为 "null" 或用户 ID
     */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /**
     * 子实体
     * 在响应中表示实体包含的子元素
     */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /**
     * 客户端版本
     * 标识客户端使用的 API 版本号
     * 在登录时从服务器获取
     */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /**
     * 完成状态
     * 标识任务是否已完成
     * true 表示已完成，false 表示未完成
     */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /**
     * 当前列表 ID
     * 标识任务当前所属的任务列表
     */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /**
     * 默认列表 ID
     * 用户默认的任务列表 ID
     */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /**
     * 删除标记
     * 标识任务或任务列表是否已被删除
     * true 表示已删除，false 表示未删除
     */
    public final static String GTASK_JSON_DELETED = "deleted";

    /**
     * 目标列表
     * 移动操作时，指定任务要移动到的目标任务列表
     */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /**
     * 目标父节点
     * 移动操作时，指定任务要移动到的目标父节点
     */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /**
     * 目标父节点类型
     * 标识目标父节点的类型（如 "GROUP"）
     */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /**
     * 实体变化部分
     * 在更新操作中，只包含变化的字段，而不是整个实体
     * 用于减少网络传输量
     */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /**
     * 实体类型
     * 标识实体是任务（TASK）还是任务列表（GROUP）
     */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /**
     * 是否获取已删除的项
     * 在 get_all 操作中，指定是否返回已删除的任务
     */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /**
     * 实体 ID
     * 任务或任务列表的唯一标识符
     */
    public final static String GTASK_JSON_ID = "id";

    /**
     * 索引位置
     * 标识任务或任务列表在列表中的位置
     * 用于排序
     */
    public final static String GTASK_JSON_INDEX = "index";

    /**
     * 最后修改时间
     * 任务或任务列表的最后修改时间戳
     * 用于同步冲突检测
     */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /**
     * 最新同步点
     * 用于增量同步，标识上次同步的位置
     */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    /**
     * 列表 ID
     * 任务列表的唯一标识符
     */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /**
     * 任务列表集合
     * 响应中包含所有任务列表的 JSON 数组
     */
    public final static String GTASK_JSON_LISTS = "lists";

    /**
     * 名称
     * 任务或任务列表的名称
     */
    public final static String GTASK_JSON_NAME = "name";

    /**
     * 新 ID
     * 创建操作成功后，服务器返回的新实体 ID
     */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /**
     * 备注
     * 任务的备注内容（详细信息）
     */
    public final static String GTASK_JSON_NOTES = "notes";

    /**
     * 父节点 ID
     * 标识任务的父任务列表
     */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /**
     * 前兄弟节点 ID
     * 用于维护任务在列表中的顺序
     * 标识在当前任务之前的那个任务的 ID
     */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /**
     * 结果集
     * 响应的 JSON 数组，包含每个操作的结果
     */
    public final static String GTASK_JSON_RESULTS = "results";

    /**
     * 源列表
     * 移动操作时，标识任务原来所属的任务列表
     */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /**
     * 任务集合
     * 响应中包含任务列表内所有任务的 JSON 数组
     */
    public final static String GTASK_JSON_TASKS = "tasks";

    /**
     * 类型
     * 通用类型字段
     */
    public final static String GTASK_JSON_TYPE = "type";

    /**
     * 实体类型：分组
     * 标识这是一个任务列表（GROUP）
     */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /**
     * 实体类型：任务
     * 标识这是一个任务（TASK）
     */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    /**
     * 用户信息
     * 响应中包含的用户相关信息
     */
    public final static String GTASK_JSON_USER = "user";

    // ==================== MIUI 便签专用常量 ====================

    /**
     * MIUI 文件夹前缀
     *
     * 用于区分小米便签创建的文件夹和用户在 Google Tasks 中直接创建的文件夹。
     *
     * 为什么需要前缀？
     * - 避免与用户原有的任务列表命名冲突
     * - 便于识别哪些任务列表是由小米便签同步创建的
     * - 同步时只处理带有此前缀的任务列表
     *
     * 示例：
     * - 用户创建文件夹 "工作" → 远程任务列表名称 "[MIUI_Notes]工作"
     * - 用户创建文件夹 "个人" → 远程任务列表名称 "[MIUI_Notes]个人"
     */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /**
     * 默认文件夹名称
     *
     * 对应本地数据库中的根文件夹（ID_ROOT_FOLDER）
     * 用于存储不在任何用户创建文件夹中的便签
     *
     * 远程任务列表名称： "[MIUI_Notes]Default"
     */
    public final static String FOLDER_DEFAULT = "Default";

    /**
     * 通话记录文件夹名称
     *
     * 对应本地数据库中的通话记录文件夹（ID_CALL_RECORD_FOLDER）
     * 用于存储通话记录类型的便签
     *
     * 远程任务列表名称： "[MIUI_Notes]Call_Note"
     */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /**
     * 元数据文件夹名称
     *
     * 这是一个特殊的任务列表，用于存储所有元数据任务。
     * 元数据记录了本地便签与远程任务的映射关系。
     *
     * 远程任务列表名称： "[MIUI_Notes]METADATA"
     *
     * 元数据列表中的每个任务都是一个 MetaData 对象，
     * 其 notes 字段存储了本地便签的完整元数据（JSON 格式）。
     */
    public final static String FOLDER_META = "METADATA";

    // ==================== 元数据 JSON 键名常量 ====================

    /**
     * 元数据头：GTASK ID
     *
     * 在元数据 JSON 中，记录远程任务的 GID。
     *
     * 元数据 JSON 示例：
     * {
     *   "meta_gid": "task_gid_123",
     *   "meta_note": { ... },
     *   "meta_data": [ ... ]
     * }
     */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /**
     * 元数据头：便签信息
     *
     * 存储本地便签的基本信息（ID、类型、修改时间等）
     */
    public final static String META_HEAD_NOTE = "meta_note";

    /**
     * 元数据头：数据信息
     *
     * 存储本地便签的详细数据内容（文本、通话记录等）
     */
    public final static String META_HEAD_DATA = "meta_data";

    /**
     * 元数据节点名称
     *
     * 元数据任务的固定名称，用于在 Google Tasks 中标识这是一个元数据任务。
     *
     * 内容："[META INFO] DON'T UPDATE AND DELETE"
     *
     * 警告信息：
     * - DON'T UPDATE - 不要更新这个任务的内容
     * - DON'T DELETE - 不要删除这个任务
     *
     * 为什么需要这个警告？
     * 元数据是同步功能的核心，如果被用户误修改或删除，
     * 会导致本地便签与远程任务的映射关系丢失，同步功能会出问题。
     */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";
}