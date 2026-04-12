package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 元数据类
 *
 * 继承自Task，用于存储便签与Google Tasks之间的映射关系。
 * 在GTask同步过程中，元数据记录了本地便签对应的远程任务ID，
 * 以便在同步时能够正确匹配和更新。
 *
 * 元数据本身也是一个特殊的任务节点，存储在Google Tasks中，
 * 其名称固定为"meta-note"，内容为JSON格式的映射信息。
 */
public class MetaData extends Task {

    // 日志标签
    private final static String TAG = MetaData.class.getSimpleName();

    // 关联的远程GTask ID（即便签对应的Google任务ID）
    private String mRelatedGid = null;

    /**
     * 设置元数据信息
     *
     * 创建一个元数据节点，用于记录本地便签与远程任务的对应关系
     *
     * @param gid 远程GTask ID（Google任务的唯一标识）
     * @param metaInfo 元数据JSON对象，会添加gid字段后存储到notes中
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将远程任务ID添加到元数据JSON对象中
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        // 将JSON对象转为字符串存储到notes字段
        setNotes(metaInfo.toString());
        // 设置元数据节点的固定名称（用于在Google Tasks中标识这是元数据节点）
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取关联的远程GTask ID
     *
     * @return 远程任务ID，如果没有则返回null
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断是否需要保存到服务器
     * 只要notes字段不为空就需要保存
     *
     * @return true表示需要保存
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程JSON数据设置内容
     *
     * 解析从Google Tasks服务器获取的JSON数据，提取出关联的远程任务ID
     *
     * @param js 远程服务器返回的JSON对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        // 调用父类方法设置基本属性
        super.setContentByRemoteJSON(js);

        // 如果notes字段不为空，尝试解析出关联的GTask ID
        if (getNotes() != null) {
            try {
                // 解析notes中的JSON字符串
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                // 提取远程任务ID
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 从本地JSON数据设置内容
     *
     * 元数据不应该从本地JSON创建，调用此方法会抛出异常
     *
     * @param js 本地JSON对象
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // 此函数不应被调用，元数据只能从服务器获取
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 从本地内容获取JSON对象
     *
     * 元数据不应该从本地生成JSON，调用此方法会抛出异常
     *
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 获取同步操作类型
     *
     * 元数据不需要同步操作判断，调用此方法会抛出异常
     *
     * @param c 数据库游标
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }
}