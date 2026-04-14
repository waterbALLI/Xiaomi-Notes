package net.micode.notes.gtask.remote;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Google Tasks 客户端类
 *
 * 这个类是小米便签与 Google Tasks 云端同步的核心网络通信层。
 * 它采用单例模式设计，确保整个应用只有一个实例管理所有同步操作。
 *
 * 主要职责：
 * 1. 管理与 Google 账号的认证和登录
 * 2. 封装所有与 Google Tasks 服务器通信的 HTTP 请求
 * 3. 提供任务和任务列表的 CRUD 操作接口
 * 4. 支持批量操作以提高网络效率
 * 5. 处理网络错误和认证失效等异常情况
 *
 * 技术要点：
 * - 使用 Apache HttpClient 进行 HTTP 通信
 * - 使用 Android AccountManager 进行账号认证
 * - 使用 JSON 作为数据交换格式
 * - 支持 GZIP/Deflate 压缩以减少网络流量
 * - Cookie 管理维护登录状态
 *
 * @see DefaultHttpClient
 * @see AccountManager
 * @see JSONObject
 */
public class GTaskClient {

    // ==================== 常量定义 ====================

    /**
     * 日志标签，用于 Logcat 输出时标识来源
     */
    private static final String TAG = GTaskClient.class.getSimpleName();

    /**
     * Google Tasks 服务的根 URL
     * 用于构建自定义域名的访问地址
     */
    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    /**
     * Google Tasks 的 GET 请求 URL
     * 用于获取初始数据、客户端版本和建立会话
     * 访问这个 URL 会返回包含 _setup() 函数调用的 JavaScript
     */
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    /**
     * Google Tasks 的 POST 请求 URL
     * 用于提交各种操作（创建、更新、删除、移动等）
     * 所有操作都通过向这个 URL 发送 POST 请求来完成
     */
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    /**
     * 单例实例
     * 使用 volatile 和 synchronized 保证线程安全
     */
    private static GTaskClient mInstance = null;

    // ==================== 实例变量 ====================

    /**
     * HTTP 客户端
     * 使用 Apache HttpClient 实现，管理连接、Cookie 等
     */
    private DefaultHttpClient mHttpClient;

    /**
     * GET 请求的 URL
     * 对于普通 Gmail 账号使用 GTASK_GET_URL
     * 对于 Google Apps 自定义域名账号会动态构建
     * 例如：https://mail.google.com/tasks/a/example.com/ig
     */
    private String mGetUrl;

    /**
     * POST 请求的 URL
     * 对于普通 Gmail 账号使用 GTASK_POST_URL
     * 对于 Google Apps 自定义域名账号会动态构建
     */
    private String mPostUrl;

    /**
     * 客户端版本号
     * 从服务器获取，用于标识客户端协议版本
     * 每次登录时从服务器响应中解析
     */
    private long mClientVersion;

    /**
     * 登录状态标志
     * true 表示已经成功登录并获得有效的 Cookie
     * false 表示需要重新登录
     */
    private boolean mLoggedin;

    /**
     * 最后登录时间（毫秒时间戳）
     * 用于判断 Cookie 是否过期
     * Google Tasks 的 Cookie 有效期约为 5 分钟
     */
    private long mLastLoginTime;

    /**
     * 操作 ID 计数器
     * 每次发送请求时递增，用于在响应中匹配对应的请求
     * 服务器会在响应中返回相同的 action_id
     */
    private int mActionId;

    /**
     * 当前登录的 Google 账号
     * 用于判断账号是否切换，以及构建自定义域名 URL
     */
    private Account mAccount;

    /**
     * 待提交的更新操作数组
     * 批量操作时，多个更新会先存储在这个数组中
     * 调用 commitUpdate() 时一次性提交
     * 这样可以减少网络请求次数，提高效率
     */
    private JSONArray mUpdateArray;

    /**
     * 私有构造函数（单例模式）
     *
     * 初始化所有成员变量为默认值。
     * 注意：此时并没有创建 HttpClient，真正的 HttpClient 在登录时创建。
     * 这样可以避免在网络不可用时创建无用的对象。
     */
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;           // 初始使用默认 URL
        mPostUrl = GTASK_POST_URL;         // 初始使用默认 URL
        mClientVersion = -1;               // -1 表示尚未获取
        mLoggedin = false;                 // 初始未登录
        mLastLoginTime = 0;                // 上次登录时间为 0
        mActionId = 1;                     // 从 1 开始计数
        mAccount = null;                   // 尚未关联账号
        mUpdateArray = null;               // 无待提交更新
    }

    /**
     * 获取单例实例（线程安全）
     *
     * 使用双重检查锁定（DCL）模式确保线程安全的同时保持性能。
     *
     * @return GTaskClient 单例对象
     */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    // ==================== 登录相关方法 ====================

    /**
     * 登录 Google Tasks 服务
     *
     * 这是使用 GTaskClient 前必须调用的方法。
     * 登录过程较为复杂，包含多个步骤：
     *
     * 步骤 1：检查登录状态是否仍然有效
     *         - Cookie 有效期约 5 分钟，超时需要重新登录
     *         - 检查用户是否在设置中切换了账号
     *
     * 步骤 2：获取 Google 账号的 AuthToken
     *         - 通过 AccountManager 获取
     *         - 使用 "goanna_mobile" 作用域
     *
     * 步骤 3：判断账号类型
     *         - 普通 Gmail 账号（@gmail.com 或 @googlemail.com）
     *         - Google Apps 自定义域名账号（如 @company.com）
     *         对于自定义域名，需要构建带域名的特殊 URL
     *
     * 步骤 4：执行登录请求
     *         - 向 Google Tasks 发送带 AuthToken 的 GET 请求
     *         - 解析返回的 JavaScript 获取客户端版本
     *         - 保存返回的 Cookie 用于后续请求
     *
     * 步骤 5：处理 Token 过期
     *         - 如果登录失败，可能 Token 已过期
     *         - 使 Token 失效后重新获取
     *         - 重试登录
     *
     * @param activity 用于 AccountManager 的 Activity
     *                 AccountManager 需要 Activity 来弹出账号选择对话框
     * @return true 登录成功，false 登录失败
     */
    public boolean login(Activity activity) {
        // Cookie 有效期 5 分钟（300,000 毫秒）
        // 这是 Google Tasks 服务端的 Cookie 过期策略
        final long interval = 1000 * 60 * 5;

        // 检查 Cookie 是否过期
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
            Log.d(TAG, "Cookie expired, need to re-login");
        }

        // 检查账号是否切换
        // 用户在设置中修改了同步账号，需要重新登录
        if (mLoggedin && mAccount != null
                && !TextUtils.equals(mAccount.name,
                NotesPreferenceActivity.getSyncAccountName(activity))) {
            mLoggedin = false;
            Log.d(TAG, "Account changed, need to re-login");
        }

        // 如果已经登录，直接返回成功
        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        // 记录本次登录时间
        mLastLoginTime = System.currentTimeMillis();

        // 获取 Google 账号的 AuthToken
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // ===== 处理 Google Apps 自定义域名账号 =====
        // 判断是否为自定义域名（非 Gmail）
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") ||
                mAccount.name.toLowerCase().endsWith("googlemail.com"))) {

            // 构建自定义域名的 URL
            // 例如：账号 user@example.com
            // 构建结果：https://mail.google.com/tasks/a/example.com/
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;  // '@' 后面的位置
            String suffix = mAccount.name.substring(index);  // 域名部分
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            Log.d(TAG, "Using custom domain URL: " + mGetUrl);

            // 尝试使用自定义域名登录
            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
                Log.d(TAG, "Login success with custom domain");
            }
        }

        // ===== 使用官方 URL 登录 =====
        // 如果自定义域名登录失败，或者本来就是 Gmail 账号
        if (!mLoggedin) {
            // 恢复为官方 URL
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;

            Log.d(TAG, "Using official URL: " + mGetUrl);

            // 尝试使用官方 URL 登录
            if (!tryToLoginGtask(activity, authToken)) {
                Log.e(TAG, "Login failed with official URL");
                return false;
            }
        }

        mLoggedin = true;
        Log.d(TAG, "Login success");
        return true;
    }

    /**
     * 登录 Google 账号获取 AuthToken
     *
     * AuthToken 是 OAuth 2.0 风格的访问令牌，用于证明应用已被授权访问用户数据。
     *
     * 工作流程：
     * 1. 获取 AccountManager 服务
     * 2. 获取设备上所有 Google 账号
     * 3. 找到用户在设置中选择的同步账号
     * 4. 调用 getAuthToken() 获取令牌
     * 5. 如果需要使令牌失效，则调用 invalidateAuthToken()
     *
     * 关于 "goanna_mobile" 作用域：
     * 这是 Google Tasks 移动客户端使用的专用作用域
     * 它提供了访问 Google Tasks 数据的权限
     *
     * @param activity Activity 上下文
     * @param invalidateToken true 表示使现有 token 失效并重新获取
     * @return AuthToken 字符串，失败返回 null
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);

        // 获取设备上所有 Google 账号
        Account[] accounts = accountManager.getAccountsByType("com.google");

        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        // 查找用户在设置中指定的同步账号
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }

        if (account != null) {
            mAccount = account;
            Log.d(TAG, "Found sync account: " + accountName);
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // 获取 AuthToken
        // 参数说明：
        // - account: 要获取令牌的账号
        // - "goanna_mobile": 认证作用域，Google Tasks 专用
        // - null: 认证选项（Bundle），不需要额外选项
        // - activity: 如果需要用户交互（如重新输入密码）会用到
        // - null: 回调，这里同步等待结果
        // - null: Handler
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);

            // 如果需要使令牌失效
            // 通常在令牌过期或验证失败时调用
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                Log.d(TAG, "Invalidated old auth token, getting new one...");
                // 递归调用，重新获取新令牌
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed: " + e.toString());
            authToken = null;
        }

        return authToken;
    }

    /**
     * 尝试登录 Google Tasks（带重试机制）
     *
     * 这个方法封装了登录重试逻辑：
     * 1. 首先尝试用当前 token 登录
     * 2. 如果失败，使 token 失效后重新获取
     * 3. 用新 token 再次尝试登录
     *
     * @param activity Activity 上下文
     * @param authToken 认证令牌
     * @return true 登录成功，false 登录失败
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        // 第一次尝试
        if (!loginGtask(authToken)) {
            Log.w(TAG, "First login attempt failed, invalidating token and retrying...");

            // Token 可能过期，使其失效后重新获取
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            // 第二次尝试
            if (!loginGtask(authToken)) {
                Log.e(TAG, "Second login attempt also failed");
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 Google Tasks 登录（核心登录逻辑）
     *
     * 这个方法完成实际的 HTTP 登录请求：
     *
     * 1. 创建 HttpClient 并配置超时参数
     *    - 连接超时：10 秒
     *    - Socket 超时：15 秒
     *    - 使用 BasicCookieStore 管理 Cookie
     *
     * 2. 发送 GET 请求
     *    URL 格式：{mGetUrl}?auth={authToken}
     *    例如：https://mail.google.com/tasks/ig?auth=xxx
     *
     * 3. 验证响应中的 Cookie
     *    成功登录后，服务器会返回包含 "GTL" 的认证 Cookie
     *
     * 4. 解析响应内容获取客户端版本
     *    响应格式：一个包含 _setup() 函数调用的 JavaScript
     *    例如：_setup({"v": 1, ...})</script>
     *    需要从中提取 JSON 部分并解析 v 字段
     *
     * @param authToken 认证令牌
     * @return true 登录成功，false 登录失败
     */
    private boolean loginGtask(String authToken) {
        // 配置 HTTP 超时参数
        int timeoutConnection = 10000;  // 连接超时：10 秒
        int timeoutSocket = 15000;      // Socket 读取超时：15 秒

        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

        // 创建 HttpClient
        mHttpClient = new DefaultHttpClient(httpParameters);

        // 设置 Cookie 存储
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);

        // 禁用 Expect: 100-continue 头
        // 这可以减少一次 HTTP 往返，提高速度
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // 构建登录 URL
        String loginUrl = mGetUrl + "?auth=" + authToken;
        HttpGet httpGet = new HttpGet(loginUrl);

        try {
            Log.d(TAG, "Logging in with URL: " + loginUrl);
            HttpResponse response = mHttpClient.execute(httpGet);

            // 验证认证 Cookie
            // 登录成功后，服务器会返回一个名为 "GTL" 的 Cookie
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                    Log.d(TAG, "Found auth cookie: " + cookie.getName());
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // 解析客户端版本
            // 服务器返回的内容示例：
            // <script>_setup({"v": 1, "t": {...}})</script>
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);

            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                // 提取 JSON 部分
                jsString = resString.substring(begin + jsBegin.length(), end);
                Log.d(TAG, "Extracted JSON: " + jsString);
            } else {
                Log.e(TAG, "Failed to extract _setup() content");
                return false;
            }

            // 解析 JSON 获取客户端版本
            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v");
            Log.d(TAG, "Client version: " + mClientVersion);

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing failed: " + e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "HTTP GET failed: " + e.toString());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取下一个操作 ID
     *
     * 每个请求都需要一个唯一的 action_id，服务器会在响应中返回相同的 ID。
     * 这样可以用于：
     * 1. 将请求与响应进行匹配
     * 2. 调试和日志追踪
     *
     * @return 递增的操作 ID
     */
    private int getActionId() {
        return mActionId++;
    }

    /**
     * 创建 HTTP POST 请求对象
     *
     * 为每个 POST 请求设置必要的请求头：
     * - Content-Type: application/x-www-form-urlencoded;charset=utf-8
     *   告诉服务器请求体的格式
     * - AT: 1
     *   这是一个自定义头，可能是 Google Tasks 用于识别客户端的标识
     *
     * @return 配置好的 HttpPost 对象
     */
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    /**
     * 从 HTTP 响应中获取内容字符串
     *
     * 这个方法处理了三种情况：
     * 1. 未压缩的响应：直接读取
     * 2. GZIP 压缩的响应：使用 GZIPInputStream 解压
     * 3. Deflate 压缩的响应：使用 InflaterInputStream 解压
     *
     * 压缩可以显著减少网络传输量，Google Tasks 默认会返回压缩响应。
     *
     * @param entity HTTP 实体
     * @return 响应内容字符串
     * @throws IOException IO 异常
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        // 检查内容编码
        String contentEncoding = null;
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "Content encoding: " + contentEncoding);
        }

        // 根据编码类型选择解压方式
        InputStream input = entity.getContent();
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            // GZIP 解压
            input = new GZIPInputStream(entity.getContent());
            Log.d(TAG, "Using GZIP decompression");
        } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            // Deflate 解压（ZLIB 格式，带头部）
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
            Log.d(TAG, "Using Deflate decompression");
        }

        // 读取解压后的内容
        try {
            InputStreamReader isr = new InputStreamReader(input, "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            Log.d(TAG, "Response content length: " + sb.length() + " chars");
            return sb.toString();
        } finally {
            input.close();  // 确保关闭输入流
        }
    }

    /**
     * 发送 POST 请求到 Google Tasks
     *
     * 请求格式：
     * POST /tasks/r/ig HTTP/1.1
     * Content-Type: application/x-www-form-urlencoded
     * AT: 1
     *
     * r={"action_list": [...], "client_version": 1}
     *
     * 响应格式：
     * {
     *   "results": [...],
     *   "success": true
     * }
     *
     * @param js 请求的 JSON 对象
     * @return 响应的 JSON 对象
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "not logged in");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();
        try {
            // 构建请求体
            // 格式：r={JSON字符串}
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            Log.d(TAG, "POST request body: " + js.toString());

            // 执行请求
            HttpResponse response = mHttpClient.execute(httpPost);
            String jsString = getResponseContent(response.getEntity());
            Log.d(TAG, "POST response: " + jsString);

            return new JSONObject(jsString);

        } catch (ClientProtocolException e) {
            Log.e(TAG, "Client protocol error: " + e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed: protocol error", e);
        } catch (IOException e) {
            Log.e(TAG, "IO error: " + e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed: IO error", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error: " + e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request", e);
        }
    }

    // ==================== 任务操作 API ====================

    /**
     * 创建任务
     *
     * 流程：
     * 1. 先提交任何待处理的更新（保持操作顺序）
     * 2. 构建包含创建操作的 JSON 请求
     * 3. 发送请求
     * 4. 从响应中提取新任务的 GID
     *
     * @param task 要创建的任务对象（需要已经设置好名称、备注等属性）
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public void createTask(Task task) throws NetworkFailureException {
        // 先提交待处理的更新，保持操作顺序
        commitUpdate();

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 添加创建操作
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求
            JSONObject jsResponse = postRequest(jsPost);

            // 从响应中提取新任务的 GID
            // 响应格式：{"results": [{"new_id": "xxx"}]}
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            String newGid = jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID);
            task.setGid(newGid);

            Log.d(TAG, "Task created successfully, GID: " + newGid);

        } catch (JSONException e) {
            Log.e(TAG, "JSON handling failed: " + e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed", e);
        }
    }

    /**
     * 创建任务列表
     *
     * 任务列表在 Google Tasks 中称为"任务分组"或"列表"。
     * 创建流程与创建任务类似。
     *
     * @param tasklist 要创建的任务列表对象
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

            Log.d(TAG, "TaskList created successfully, GID: " + tasklist.getGid());

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed", e);
        }
    }

    /**
     * 提交所有待处理的更新（批量提交）
     *
     * 批量提交的好处：
     * 1. 减少网络请求次数（多个操作一次发送）
     * 2. 保证原子性（所有操作要么都成功，要么都失败）
     * 3. 减少网络延迟的影响
     *
     * 更新队列会在以下情况自动提交：
     * - 添加更新时队列长度超过 10
     * - 执行需要立即生效的操作（如创建、移动、删除）
     * - 用户手动调用 commitUpdate()
     *
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null && mUpdateArray.length() > 0) {
            Log.d(TAG, "Committing " + mUpdateArray.length() + " updates");
            try {
                JSONObject jsPost = new JSONObject();

                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                postRequest(jsPost);
                mUpdateArray = null;  // 清空队列

                Log.d(TAG, "Updates committed successfully");

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed", e);
            }
        } else {
            Log.d(TAG, "No pending updates to commit");
        }
    }

    /**
     * 添加待更新的节点（批量操作）
     *
     * 这个方法用于将多个更新操作收集到队列中。
     * 当队列长度超过 10 时，会自动提交，避免队列过大。
     *
     * 为什么限制为 10？
     * - 避免 HTTP 请求体过大
     * - 防止服务器拒绝处理
     * - 保持合理的响应时间
     *
     * @param node 要更新的节点（Task 或 TaskList）
     * @throws NetworkFailureException 网络错误时抛出
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // 如果队列已存在且超过 10 个，先提交
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                Log.d(TAG, "Update queue size > 10, committing...");
                commitUpdate();
            }

            // 创建队列（如果需要）
            if (mUpdateArray == null) {
                mUpdateArray = new JSONArray();
            }

            // 添加更新操作
            mUpdateArray.put(node.getUpdateAction(getActionId()));
            Log.d(TAG, "Added update to queue, current size: " + mUpdateArray.length());
        }
    }

    /**
     * 移动任务
     *
     * 移动任务可以发生在两种情况：
     * 1. 在同一任务列表内移动（改变顺序）
     * 2. 移动到不同的任务列表
     *
     * 移动操作需要提供：
     * - 源列表（原所在列表）
     * - 目标父节点（新父列表）
     * - 可选的前兄弟节点（用于确定位置）
     * - 目标列表（如果跨列表移动）
     *
     * @param task 要移动的任务
     * @param preParent 原父任务列表
     * @param curParent 新父任务列表
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        // 移动操作需要立即执行，先提交待处理的更新
        commitUpdate();

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 设置操作类型为 MOVE
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());

            // 仅在同一个任务列表内移动且不是第一个时，设置前兄弟任务
            // 这样可以保持任务的排序顺序
            if (preParent == curParent && task.getPriorSibling() != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID,
                        task.getPriorSibling().getGid());
            }

            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());

            // 在不同任务列表之间移动时，设置目标列表
            if (preParent != curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }

            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            Log.d(TAG, "Task moved successfully");

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed", e);
        }
    }

    /**
     * 删除节点（任务或任务列表）
     *
     * 删除操作是软删除：
     * 1. 设置节点的 deleted 标志为 true
     * 2. 发送更新请求
     *
     * 服务器收到 deleted=true 的更新后，会将该节点标记为已删除。
     * 这比物理删除更好，因为：
     * - 可以恢复（如果同步错误）
     * - 可以同步到其他客户端
     *
     * @param node 要删除的节点
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 标记为已删除并发送更新
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;  // 清空队列

            Log.d(TAG, "Node deleted successfully, GID: " + node.getGid());

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed", e);
        }
    }

    /**
     * 获取所有任务列表
     *
     * 这个方法使用 GET 请求获取用户的完整任务列表数据。
     * 返回的 JSON 包含用户的所有任务列表及其基本属性。
     *
     * 响应解析过程：
     * 1. 获取 HTML/JavaScript 响应
     * 2. 提取 _setup({...}) 中的 JSON
     * 3. 解析 JSON 获取 t.lists 数组
     *
     * @return 任务列表的 JSON 数组
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = mHttpClient.execute(httpGet);

            // 解析响应
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);

            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            } else {
                Log.e(TAG, "Failed to extract _setup() content");
                throw new ActionFailureException("Invalid response format");
            }

            JSONObject js = new JSONObject(jsString);
            // 返回任务列表数组
            // 响应结构：{"t": {"lists": [...]}}
            JSONArray taskLists = js.getJSONObject("t")
                    .getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);

            Log.d(TAG, "Retrieved " + taskLists.length() + " task lists");
            return taskLists;

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed", e);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed", e);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed", e);
        }
    }

    /**
     * 获取指定任务列表中的所有任务
     *
     * 这个方法使用 POST 请求获取指定列表的任务详情。
     * 使用 POST 而不是 GET 是因为需要发送 action_list 参数。
     *
     * 请求类型：ACTION_TYPE_GETALL
     * 这个操作告诉服务器返回指定列表中的所有任务。
     *
     * @param listGid 任务列表的 GID
     * @return 任务的 JSON 数组
     * @throws NetworkFailureException 网络错误时抛出
     * @throws ActionFailureException 协议错误时抛出
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 设置操作类型为 GETALL（获取全部任务）
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);  // 不获取已删除的任务
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            JSONArray tasks = jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);

            Log.d(TAG, "Retrieved " + tasks.length() + " tasks from list: " + listGid);
            return tasks;

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed", e);
        }
    }

    // ==================== Getter 和辅助方法 ====================

    /**
     * 获取当前同步的 Google 账号
     *
     * @return 当前登录的 Account 对象
     */
    public Account getSyncAccount() {
        return mAccount;
    }

    /**
     * 重置更新数组
     *
     * 在取消同步或发生错误时调用，清空待提交的更新队列
     */
    public void resetUpdateArray() {
        mUpdateArray = null;
        Log.d(TAG, "Update array reset");
    }
}