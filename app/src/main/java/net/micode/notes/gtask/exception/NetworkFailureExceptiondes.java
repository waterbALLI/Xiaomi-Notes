package net.micode.notes.gtask.exception;

/**
 * 网络故障异常类
 *
 * 继承自 Exception，是一个受检异常（checked exception）。
 * 专门用于表示 Google Tasks 同步过程中发生的网络相关错误。
 *
 * 与 ActionFailureException 的区别：
 * - ActionFailureException 继承 RuntimeException（非受检），用于数据解析、数据库操作等内部错误
 * - NetworkFailureException 继承 Exception（受检），用于网络连接、数据传输等外部错误
 *
 * 设计为受检异常的原因：
 * 1. 网络故障是可预期的、可恢复的错误（如用户可重试）
 * 2. 强制调用方处理网络异常，提供合适的重试或提示逻辑
 * 3. 与业务逻辑异常（如数据解析错误）区分开，便于分层处理
 *
 * 典型使用场景：
 * - HTTP 请求超时
 * - 网络连接不可用
 * - 服务器返回网络错误状态码
 * - SSL 证书验证失败
 * - 数据流读写异常
 *
 * @see Exception
 * @see ActionFailureException
 */
public class NetworkFailureException extends Exception {

    /**
     * 序列化版本UID
     * 用于对象序列化时的版本兼容性验证
     *
     * 由于网络异常可能跨进程传递（如在同步服务与UI线程之间），
     * 定义明确的 UID 可以确保序列化/反序列化的兼容性
     */
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 无参构造函数
     * 创建一个没有详细消息和原因的异常实例
     *
     * 适用于未知类型的网络错误
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 带详细消息的构造函数
     *
     * @param paramString 异常的详细描述信息，如 "HTTP request timeout"、"No network connection" 等
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带详细消息和原因的构造函数
     *
     * @param paramString 异常的详细描述信息
     * @param paramThrowable 导致当前异常的根本原因（原始异常，如 IOException、SocketTimeoutException 等）
     *
     * 示例：
     * try {
     *     httpClient.execute(request);
     * } catch (IOException e) {
     *     throw new NetworkFailureException("网络请求失败", e);
     * }
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}