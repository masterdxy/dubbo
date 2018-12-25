package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;
import com.alibaba.dubbo.rpc.utils.DubboTraceUtils;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.context.TracingUtils;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

/**
 * @author: dongxinyu
 * @date: 17/5/23 上午11:31
 */
@Activate(group = Constants.PROVIDER, order = -20000)
public class RPCProviderTraceFilter implements Filter{

	private Logger logger = LoggerFactory.getLogger(RPCProviderTraceFilter.class);
	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

		//trace
		if(!invocation.getAttachments().isEmpty()){
			String traceData = invocation.getAttachment("trace");
			if(StringUtils.isNotEmpty(traceData)){
				Tracer.SpanBuilder spanBuilder = null;
				Tracer tracer = null;
				if ((invocation.getMethodName().equals(Constants.$INVOKE)
						&& invocation.getArguments() != null
						&& invocation.getArguments().length == 3)) {
					//generic
					//String realServiceName = String.valueOf(invocation.getAttachment("interface"));
					String realMethodName = String.valueOf(invocation.getArguments()[0]);
					tracer = DubboTraceUtils.getTracerByIfaceName(invoker.getInterface().getSimpleName());
					if(tracer != null){
						spanBuilder = tracer.buildSpan(realMethodName);
					}
				}else{
					tracer = DubboTraceUtils.getTracerByIfaceName(invoker.getInterface().getSimpleName());
					if(tracer != null){
						spanBuilder = tracer.buildSpan(invocation.getMethodName());
					}
				}
				try{
					if(spanBuilder != null){
						SpanContext parentSpanContext = SpanContext.contextFromString(traceData);
						spanBuilder.asChildOf(parentSpanContext);
						Span span = (Span) spanBuilder.start();
						Tags.PEER_HOSTNAME.set(span,RpcContext.getContext().getLocalHost());
						Tags.PEER_SERVICE.set(span,invoker.getInterface().getName());
						Tags.SPAN_KIND.set(span,"server");
						TracingUtils.getTraceContext().push(span);
					}
				}catch (Exception ignored){
					logger.warn("provider tracing error , interface : " + invoker.getInterface().getSimpleName() + " , error msg :" + ignored.getMessage());
				}
			}
		}

		boolean hasException = false;
		String error_msg = "";

		try{
			return invoker.invoke(invocation);
		}catch (Throwable throwable){
			if (throwable instanceof RuntimeException) {
				hasException = true;
				error_msg = throwable.getMessage();
				throw (RuntimeException) throwable;
			} else {
				throw new RpcException(throwable.getCause());
			}
		}finally {
			if (!TracingUtils.getTraceContext().isEmpty()) {
				io.opentracing.Span curr = TracingUtils.getTraceContext().pop();
				if (hasException) {
					Tags.ERROR.set(curr, true);
					((com.uber.jaeger.Span) curr).log("error_msg", error_msg);
				}
				curr.finish();
			}
		}
	}
}
