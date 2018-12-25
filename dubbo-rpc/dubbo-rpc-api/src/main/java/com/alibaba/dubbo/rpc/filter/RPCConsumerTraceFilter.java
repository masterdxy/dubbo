package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;
import com.alibaba.dubbo.rpc.utils.DubboTraceUtils;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.context.TracingUtils;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tags;

/**
 * @author: dongxinyu
 * @date: 17/5/23 上午11:31
 */
@Activate(group = Constants.CONSUMER, order = -20000)
public class RPCConsumerTraceFilter implements Filter {

    private Logger logger = LoggerFactory.getLogger(RPCConsumerTraceFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

        //set trace data to attachment
        Tracer tracer = null;
        Span consumerInvokeSpan = null;
        Tracer.SpanBuilder spanBuilder = null;

        String generic = invoker.getUrl().getParameter(Constants.GENERIC_KEY);
        if (ProtocolUtils.isGeneric(generic)
                && Constants.$INVOKE.equals(invocation.getMethodName())
                && invocation instanceof RpcInvocation) {
            //generic
            String realServiceName = String.valueOf(invoker.getUrl().getParameter(Constants.INTERFACE_KEY));
            String realMethodName = String.valueOf(invocation.getArguments()[0]);
            tracer = DubboTraceUtils.getTracerByIfaceName(realServiceName);
            if (tracer != null) {
                spanBuilder = tracer.buildSpan(invocation.getMethodName());
            }
        } else {
            tracer = DubboTraceUtils.getTracerByIfaceName(invoker.getInterface().getSimpleName());
            if (tracer != null) {
                spanBuilder = tracer.buildSpan(invocation.getMethodName());
            }
        }

        try {
            if (!TracingUtils.getTraceContext().isEmpty()) {
                Span currentSpan = TracingUtils.getTraceContext().getCurrentSpan();
                if (currentSpan != null && spanBuilder != null) {
                    spanBuilder.asChildOf(currentSpan);
                    consumerInvokeSpan = spanBuilder.start();
                    Tags.SPAN_KIND.set(consumerInvokeSpan, "client");
                    Tags.PEER_SERVICE.set(consumerInvokeSpan, invoker.getInterface().getName());
                    Tags.PEER_HOSTNAME.set(consumerInvokeSpan, RpcContext.getContext().getLocalHost());
                    new StringTag("provider.url").set(consumerInvokeSpan, invoker.getUrl().getAddress());
                    TracingUtils.getTraceContext().push(consumerInvokeSpan);
                    //传递到provider
                    invocation.getAttachments().put("trace", ((SpanContext) consumerInvokeSpan.context()).contextAsString());
                }
            }
        } catch (Exception ignored) {
            logger.warn("consumer tracing error , interface : " + invoker.getInterface().getSimpleName() + " , error msg :" + ignored.getMessage());
        }

        //invoke
        boolean hasException = false;
        String error_msg = "";

        try {
            return invoker.invoke(invocation);
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException) {
                hasException = true;
                error_msg = throwable.getMessage();
                throw (RuntimeException) throwable;
            } else {
                throw new RpcException(throwable.getCause());
            }
        } finally {
            if (!TracingUtils.getTraceContext().isEmpty()) {
                Span curr = TracingUtils.getTraceContext().pop();
                if (hasException) {
                    Tags.ERROR.set(curr, true);
                    ((com.uber.jaeger.Span) curr).log("error_msg", error_msg);
                }
                curr.finish();
            }
        }
    }

}
