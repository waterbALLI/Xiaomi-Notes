package net.micode.notes.gtask.data;

import android.database.Cursor;

import org.json.JSONObject;

/**
 * 节点抽象类
 *
 * 这是Google Tasks同步功能中的基础抽象类，定义了同步数据节点的基本结构和行为。
 * 具体的节点类型（如Task、MetaData）需要继承此类并实现其抽象方法。
 *
 * 节点代表一个可同步的数据单元，可以是：
 * - 普通任务（Task）
 * - 任务列表（TaskList）
 * - 元数据（MetaData）
 *
 * 同步操作类型定义了本地与远程数据之间的同步策略
 */
public abstract class Node {

    // ==================== 同步操作类型常量 ====================

    /**
     * 无同步操作
     * 本地和远程数据一致，不需要同步
     */
    public static final int SYNC_ACTION_NONE = 0;

    /**
     * 添加远程节点
     * 本地存在但远程不存在，需要在远程创建
     */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /**
     * 添加本地节点
     * 远程存在但本地不存在，需要在本地创建
     */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /**
     * 删除远程节点
     * 本地已删除但远程还存在，需要删除远程节点
     */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /**
     * 删除本地节点
     * 远程已删除但本地还存在，需要删除本地节点
     */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /**
     * 更新远程节点
     * 本地修改时间比远程新，需要将本地更新推送到远程
     */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /**
     * 更新本地节点
     * 远程修改时间比本地新，需要将远程更新拉取到本地
     */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /**
     * 更新冲突
     * 本地和远程都被修改，需要冲突解决策略
     */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /**
     * 同步错误
     * 同步过程中发生错误
     */
    public static final int SYNC_ACTION_ERROR = 8;

    // ==================== 成员变量 ====================

    private String mGid;           // 全局唯一标识符（Google Tasks中的节点ID）
    private String mName;          // 节点名称（任务标题或列表名称）
    private long mLastModified;    // 最后修改时间（毫秒时间戳）
    private boolean mDeleted;      // 删除标记（软删除，标记为已删除而非物理删除）

    /**
     * 构造函数
     * 初始化节点为默认状态
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // ==================== 抽象方法 ====================

    /**
     * 获取创建操作对应的JSON对象
     *
     * 当需要远程创建节点时，生成符合Google Tasks API格式的JSON数据
     *
     * @param actionId 操作ID（用于请求追踪）
     * @return 包含创建节点所需数据的JSON对象
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 获取更新操作对应的JSON对象
     *
     * 当需要远程更新节点时，生成符合Google Tasks API格式的JSON数据
     *
     * @param actionId 操作ID（用于请求追踪）
     * @return 包含更新节点所需数据的JSON对象
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 从远程JSON数据设置节点内容
     *
     * 解析从Google Tasks服务器获取的JSON数据，填充到当前节点对象
     *
     * @param js 远程服务器返回的JSON对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 从本地JSON数据设置节点内容
     *
     * 解析本地存储的JSON数据，填充到当前节点对象
     *
     * @param js 本地存储的JSON对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 从本地内容获取JSON对象
     *
     * 将当前节点对象转换为JSON格式，用于本地存储
     *
     * @return 表示当前节点的JSON对象
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 获取同步操作类型
     *
     * 根据本地数据库游标中的数据，判断应该执行哪种同步操作
     * 返回值应为上面定义的SYNC_ACTION_*常量之一
     *
     * @param c 数据库游标，指向当前节点对应的数据库记录
     * @return 同步操作类型
     */
    public abstract int getSyncAction(Cursor c);

    // ==================== Getter和Setter方法 ====================

    /**
     * 设置全局唯一标识符
     * @param gid Google Tasks中的节点ID
     */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /**
     * 设置节点名称
     * @param name 任务标题或列表名称
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * 设置最后修改时间
     * @param lastModified 毫秒时间戳
     */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /**
     * 设置删除标记
     * @param deleted true表示已删除，false表示未删除
     */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /**
     * 获取全局唯一标识符
     * @return Google Tasks中的节点ID
     */
    public String getGid() {
        return this.mGid;
    }

    /**
     * 获取节点名称
     * @return 任务标题或列表名称
     */
    public String getName() {
        return this.mName;
    }

    /**
     * 获取最后修改时间
     * @return 毫秒时间戳
     */
    public long getLastModified() {
        return this.mLastModified;
    }

    /**
     * 获取删除标记
     * @return true表示已删除，false表示未删除
     */
    public boolean getDeleted() {
        return this.mDeleted;
    }
}