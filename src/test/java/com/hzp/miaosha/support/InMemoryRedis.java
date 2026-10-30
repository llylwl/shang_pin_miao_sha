package com.hzp.miaosha.support;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存版 StringRedisTemplate 测试替身（基于 JDK 动态代理，无 Mockito 依赖）。
 * <p>
 * 用途：单元测试不依赖真实 Redis 也能验证 get / set / setIfAbsent / increment /
 * delete / hasKey、库存 Lua 脚本与 Pub/Sub 广播语义。生产代码始终走真实 Lettuce 连接。
 * </p>
 *
 * @author hzp
 */
public final class InMemoryRedis {

    private static final String PONG = "PONG";

    private final Map<String, String> store = new ConcurrentHashMap<>(64);

    private final Map<String, Long> ttlSeconds = new ConcurrentHashMap<>(64);

    private final List<String> publishedChannel = new CopyOnWriteArrayList<>();

    private final List<String> publishedMessage = new CopyOnWriteArrayList<>();

    private InMemoryRedis() {
    }

    /**
     * 创建替身实例。
     *
     * @return 替身
     */
    public static InMemoryRedis create() {
        return new InMemoryRedis();
    }

    public Map<String, String> store() {
        return store;
    }

    public Map<String, Long> ttlSeconds() {
        return ttlSeconds;
    }

    public List<String> publishedChannel() {
        return publishedChannel;
    }

    public List<String> publishedMessage() {
        return publishedMessage;
    }

    public void reset() {
        store.clear();
        ttlSeconds.clear();
        publishedChannel.clear();
        publishedMessage.clear();
    }

    /**
     * 构造 StringRedisTemplate 替身。
     *
     * @return 替身模板
     */
    public StringRedisTemplate newTemplate() {
        ValueOperations<String, String> valueOps = proxy(ValueOperations.class, this::handleValueOperation);
        RedisConnectionFactory factory = proxy(RedisConnectionFactory.class, this::handleConnectionFactory);
        return new TemplateStub(valueOps, factory, false);
    }

    /**
     * 构造一个「所有 Redis 操作都抛连接异常」的模板替身，用于验证降级逻辑。
     *
     * @return 替身模板
     */
    public StringRedisTemplate createBrokenTemplate() {
        ValueOperations<String, String> brokenOps = proxy(ValueOperations.class,
                (proxyObj, method, args) -> {
                    throw new RedisConnectionFailureException("Connection refused");
                });
        RedisConnectionFactory factory = proxy(RedisConnectionFactory.class, this::handleConnectionFactory);
        return new TemplateStub(brokenOps, factory, true);
    }

    private Object handleValueOperation(Object proxyObj, Method method, Object[] args) {
        switch (method.getName()) {
            case "get":
                return store.get((String) args[0]);
            case "setIfAbsent":
                return store.putIfAbsent((String) args[0], (String) args[1]) == null;
            case "set":
                handleSet(args);
                return null;
            case "increment":
                return handleIncrement(args);
            case "multiGet":
                List<String> keys = (List<String>) args[0];
                return keys.stream().map(store::get).toList();
            case "toString":
                return "InMemoryValueOperations";
            case "hashCode":
                return System.identityHashCode(proxyObj);
            case "equals":
                return proxyObj == args[0];
            default:
                return defaultValue(method.getReturnType());
        }
    }

    private void handleSet(Object[] args) {
        String key = (String) args[0];
        store.put(key, (String) args[1]);
        if (args.length >= 3 && args[2] instanceof Duration duration) {
            ttlSeconds.put(key, duration.toSeconds());
        }
    }

    private Long handleIncrement(Object[] args) {
        String key = (String) args[0];
        long delta = args.length >= 2 && args[1] instanceof Long value ? value : 1L;
        return Long.parseLong(store.merge(key, String.valueOf(delta),
                (oldValue, addValue) -> String.valueOf(Long.parseLong(oldValue) + Long.parseLong(addValue))));
    }

    private Object handleDelete(Object[] args) {
        if (args.length == 1 && args[0] instanceof String key) {
            ttlSeconds.remove(key);
            return store.remove(key) == null ? 0L : 1L;
        }
        if (args.length == 1 && args[0] instanceof Collection<?> keys && isRawKeyCollection(keys)) {
            long count = 0L;
            for (Object item : keys) {
                if (item instanceof byte[] bytes) {
                    String key = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    ttlSeconds.remove(key);
                    if (store.remove(key) != null) {
                        count++;
                    }
                }
            }
            return count;
        }
        if (args.length == 1 && args[0] instanceof Collection<?> keys) {
            long count = 0L;
            for (Object item : keys) {
                String key = String.valueOf(item);
                ttlSeconds.remove(key);
                if (store.remove(key) != null) {
                    count++;
                }
            }
            return count;
        }
        // RedisTemplate 内部还会调用 doDelete(byte[] key)
        if (args.length == 1 && args[0] instanceof byte[] rawKey) {
            String key = new String(rawKey, java.nio.charset.StandardCharsets.UTF_8);
            ttlSeconds.remove(key);
            return store.remove(key) == null ? 0L : 1L;
        }
        return 0L;
    }

    private static boolean isRawKeyCollection(Collection<?> values) {
        return values.isEmpty() || values.iterator().next() instanceof byte[];
    }

    private Object handleExecute(Object[] args) {
        RedisScript<?> script = (RedisScript<?>) args[0];
        List<String> keys = (List<String>) args[1];
        Object argValue = args.length >= 3 ? args[2] : null;
        String[] scriptArgs = new String[0];
        if (argValue instanceof String[] array) {
            scriptArgs = array;
        } else if (argValue instanceof String single) {
            scriptArgs = new String[]{single};
        }
        return executeScript(script.getScriptAsString(), keys, scriptArgs);
    }

    private Object handleConnectionFactory(Object proxyObj, Method method, Object[] args) {
        if ("getConnection".equals(method.getName())) {
            RedisConnection connection = proxy(RedisConnection.class, (connProxy, connMethod, connArgs) -> {
                if ("ping".equals(connMethod.getName())) {
                    return PONG;
                }
                if ("close".equals(connMethod.getName())) {
                    return null;
                }
                return defaultValue(connMethod.getReturnType());
            });
            return connection;
        }
        if ("toString".equals(method.getName())) {
            return "InMemoryRedisConnectionFactory";
        }
        return defaultValue(method.getReturnType());
    }

    /**
     * 模拟两个 Lua 脚本的语义：库存判断扣减、领取配额。
     *
     * @param script 脚本内容
     * @param keys   键
     * @param args   参数
     * @return 脚本返回值
     */
    private Long executeScript(String script, List<String> keys, String[] args) {
        String key = keys.get(0);
        String raw = store.get(key);
        long want = args.length > 0 ? Long.parseLong(args[0]) : 1L;
        if (script.contains("DECRBY") && script.contains("math.min")) {
            if (raw == null) {
                return 0L;
            }
            long current = Long.parseLong(raw);
            if (current <= 0L) {
                return 0L;
            }
            long take = Math.min(current, want);
            store.put(key, String.valueOf(current - take));
            return take;
        }
        if (raw == null) {
            return -1L;
        }
        long current = Long.parseLong(raw);
        if (current < want) {
            return -2L;
        }
        store.put(key, String.valueOf(current - want));
        return current - want;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("只支持对接口做 JDK 动态代理: " + type.getName());
        }
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        return '\0';
    }

    /**
     * 内存版 {@link StringRedisTemplate}：直接继承并覆写实际用到的方法，不依赖 Mockito / CGLIB。
     *
     * @author hzp
     */
    private final class TemplateStub extends StringRedisTemplate {

        private final ValueOperations<String, String> valueOps;

        private final RedisConnectionFactory factory;

        private final boolean broken;

        TemplateStub(ValueOperations<String, String> valueOps, RedisConnectionFactory factory, boolean broken) {
            this.valueOps = valueOps;
            this.factory = factory;
            this.broken = broken;
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOps;
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (broken) {
                throw new RedisConnectionFailureException("Connection refused");
            }
            return (T) handleExecute(new Object[]{script, keys, args});
        }

        // 注意：unlink 在 RedisOperations 中是泛型方法 unlink(K)，覆写会与 RedisTemplate 的
        // unlink(String) 产生擦除冲突；本替身不实现 unlink（生产代码只用 delete）。

        @Override
        @SuppressWarnings("unchecked")
        public Boolean delete(String key) {
            return ((Long) handleDelete(new Object[]{key})) > 0L;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Long delete(Collection<String> keys) {
            return (Long) handleDelete(new Object[]{keys});
        }

        @Override
        public Boolean hasKey(String key) {
            return store.containsKey(key);
        }

        @Override
        public Long convertAndSend(String channel, Object message) {
            publishedChannel.add(channel);
            publishedMessage.add(String.valueOf(message));
            return 1L;
        }

        @Override
        public RedisConnectionFactory getConnectionFactory() {
            return factory;
        }

        @Override
        public String toString() {
            return broken ? "BrokenStringRedisTemplate" : "InMemoryStringRedisTemplate";
        }
    }
}
