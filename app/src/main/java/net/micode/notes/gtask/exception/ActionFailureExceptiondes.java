package net.micode.notes.gtask.exception;

/**
 * 操作失败异常类
 *
 * 继承自 RuntimeException，是一个非受检异常（unchecked exception）。
 * 用于在 Google Tasks 同步操作过程中，当某个动作（Action）执行失败时抛出。
 *
 * 典型使用场景：
 * - JSON 数据解析失败
 * - 网络请求失败
 * - 数据库操作失败
 * - 数据格式验证失败
 *
 * 设计为 RuntimeException 的原因：
 * 1. 同步过程中的错误通常是不可恢复的，不需要强制调用方处理
 * 2. 简化代码结构，避免过多的 try-catch 块
 * 3. 让错误快速向上传播到合适的处理层级（如同步服务）
 *
 * @see RuntimeException
 */
public class ActionFailureException extends RuntimeException {

    /**
     * 序列化版本UID
     * 用于对象序列化时的版本兼容性验证
     * 由于该类可能被持久化或在进程间传递，定义明确的 UID 可以避免版本冲突
     */
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 无参构造函数
     * 创建一个没有详细消息和原因的异常实例
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 带详细消息的构造函数
     *
     * @param paramString 异常的详细描述信息，用于说明失败的具体原因
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带详细消息和原因的构造函数
     *
     * @param paramString 异常的详细描述信息
     * @param paramThrowable 导致当前异常的根本原因（原始异常）
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}