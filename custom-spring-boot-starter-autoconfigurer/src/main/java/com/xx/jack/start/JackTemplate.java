package com.xx.jack.start;

import org.springframework.util.SerializationUtils;
import redis.clients.jedis.Jedis;

import java.io.Serializable;

public class JackTemplate<K,V> {

    private Jedis jedis;

    public JackTemplate(RedisConfig redisConfig) {
        jedis = new Jedis(redisConfig.getHost(),redisConfig.getPort());
    }

    public String put(K k,V v) {

        System.out.println("-------加入缓存------");
        System.out.println("key----:"+k);
        System.out.println("value----:"+v);
        final String keyString = k.toString();
        final Object valuef = v;
        final long liveTime = 86400;
        byte[] keyb = keyString.getBytes();
        byte[] valueb = SerializationUtils.serialize((Serializable) valuef);
        return jedis.set(keyb,valueb);
    }

    public Object get(K k) {
        final String keyf = k.toString();
        byte[] key = keyf.getBytes();
        byte[] bytes = jedis.get(key);
        return SerializationUtils.deserialize(bytes);
    }
}
