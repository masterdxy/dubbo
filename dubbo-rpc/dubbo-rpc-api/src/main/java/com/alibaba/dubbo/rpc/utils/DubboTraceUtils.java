package com.alibaba.dubbo.rpc.utils;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.uber.jaeger.Configuration;
import com.uber.jaeger.samplers.RemoteControlledSampler;
import io.opentracing.Tracer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: dongxinyu
 * @date: 17/5/23 上午11:54
 */
public class DubboTraceUtils {

    private static Map<String, com.uber.jaeger.Tracer> interfaceTracerMap = new ConcurrentHashMap<String, com.uber.jaeger.Tracer>();
    private static Map<String, Configuration> configurationMap = new ConcurrentHashMap<String, Configuration>();
    private static final Object lock = new Object();
    private static Logger logger = LoggerFactory.getLogger(DubboTraceUtils.class);


    public static Tracer getTracerByIfaceName(String interfaceName) {
        if(StringUtils.isBlank(interfaceName)){
            return null;
        }
        if (interfaceTracerMap.get(interfaceName) != null) {
            return interfaceTracerMap.get(interfaceName);
        }
        synchronized (lock) {
            if (interfaceTracerMap.get(interfaceName) != null) {
                return interfaceTracerMap.get(interfaceName);
            }
            try {
                Configuration configuration = new Configuration(
                        interfaceName,
                        //todo change to remote control sampler
                        new Configuration.SamplerConfiguration(
                                RemoteControlledSampler.TYPE,
                                Double.parseDouble(ConfigUtils.getProperty("remote.default.prob", "0.001")),
                                ConfigUtils.getProperty("remote.sampler.url", "127.0.0.1:5776")),
                        new Configuration.ReporterConfiguration());
                com.uber.jaeger.Tracer tracer = (com.uber.jaeger.Tracer) configuration.getTracer();
                configurationMap.put(interfaceName, configuration);
                interfaceTracerMap.put(interfaceName, tracer);
                return tracer;
            } catch (Exception ignore) {
                logger.warn("trace init error interface : " + interfaceName + " , error : " + ignore.getMessage());
            }
            return null;
        }
    }

    public static void close(String interfaceName) {
        com.uber.jaeger.Tracer tracer = interfaceTracerMap.get(interfaceName);
        if (tracer != null) {
            try {
                tracer.close();
            } catch (Exception ignore) {
                logger.warn("trace close error interface : " + interfaceName + " , error : " + ignore.getMessage());
            }finally {
                interfaceTracerMap.remove(interfaceName);
            }
        }
    }

}
