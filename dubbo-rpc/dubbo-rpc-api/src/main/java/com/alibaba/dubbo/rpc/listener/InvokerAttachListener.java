package com.alibaba.dubbo.rpc.listener;

import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.InvokerListener;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.utils.DubboTraceUtils;

import static com.alibaba.dubbo.common.Constants.CONSUMER;
import static com.alibaba.dubbo.common.Constants.DEFAULT_PROTOCOL;
import static com.alibaba.dubbo.common.Constants.PROVIDER;

/**
 * Created by tomoyo on 2017/7/3.
 */
@Activate(value = "invokertracelistener", group = CONSUMER)
public class InvokerAttachListener implements InvokerListener {

    private Logger logger = LoggerFactory.getLogger(InvokerAttachListener.class);


    @Override
    public void referred(Invoker<?> invoker) throws RpcException {
        if (DEFAULT_PROTOCOL.equalsIgnoreCase(invoker.getUrl().getProtocol())) {
            String service_simple_name = invoker.getInterface().getSimpleName();
            DubboTraceUtils.getTracerByIfaceName(service_simple_name);
            logger.info("consumer tracer for " + DEFAULT_PROTOCOL + " started");
        }
    }

    @Override
    public void destroyed(Invoker<?> invoker) {
        String service_simple_name = invoker.getInterface().getSimpleName();
        DubboTraceUtils.close(service_simple_name);
        logger.info("consumer tracer for " + DEFAULT_PROTOCOL + "stopped");
    }

}
