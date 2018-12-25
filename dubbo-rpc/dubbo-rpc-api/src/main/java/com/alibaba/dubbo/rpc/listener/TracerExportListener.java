package com.alibaba.dubbo.rpc.listener;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.ExporterListener;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.utils.DubboTraceUtils;

import static com.alibaba.dubbo.common.Constants.DEFAULT_PROTOCOL;
import static com.alibaba.dubbo.common.Constants.PROVIDER;

/**
 * Created by tomoyo on 2017/7/3.
 */
@Activate(value = "tracelistener",group = PROVIDER)
public class TracerExportListener extends ExporterListenerAdapter {

    private Logger logger = LoggerFactory.getLogger(TracerExportListener.class);

    @Override
    public void exported(Exporter<?> exporter) throws RpcException {
        if (DEFAULT_PROTOCOL.equalsIgnoreCase(exporter.getInvoker().getUrl().getProtocol())) {
            String service_simple_name = exporter.getInvoker().getInterface().getSimpleName();
            DubboTraceUtils.getTracerByIfaceName(service_simple_name);
            logger.info("service provider tracer for " + DEFAULT_PROTOCOL + " protocol exported");
        }
    }

    @Override
    public void unexported(Exporter<?> exporter) {
        if (DEFAULT_PROTOCOL.equalsIgnoreCase(exporter.getInvoker().getUrl().getProtocol())) {
            String service_simple_name = exporter.getInvoker().getInterface().getSimpleName();
            DubboTraceUtils.close(service_simple_name);
            logger.info("service tracer for " + DEFAULT_PROTOCOL + " stopped");
        }
    }
}
