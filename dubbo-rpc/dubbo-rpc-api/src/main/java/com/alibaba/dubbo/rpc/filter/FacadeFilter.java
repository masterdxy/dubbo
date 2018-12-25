package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.service.GenericException;
import com.alibaba.dubbo.rpc.support.ProtocolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author: dongxinyu
 * @date: 17/5/5 下午4:57
 */
@Activate(group = Constants.PROVIDER, order = -20000)
public class FacadeFilter implements Filter {

	private static Logger logger = LoggerFactory.getLogger(FacadeFilter.class);

	@Override
	public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {

		if (inv.getMethodName().equals(Constants.$INVOKE)
				&& inv.getArguments() != null
				&& inv.getArguments().length == 3
				&& !ProtocolUtils.isGeneric(invoker.getUrl().getParameter(Constants.GENERIC_KEY))) {


			/**
			 * return genericService.$invoke(
			 method,
			 new String[]{Object.class.getName(), Map.class.getName()},
			 new Object[]{data, attachment}
			 );
			 */

			String methodName = ((String) inv.getArguments()[0]).trim();//method
			//not used
			//String[] argsClassNames = (String[]) inv.getArguments()[1];//new String[]{Object.class.getName(), Map.class.getName()}
			Object[] args = (Object[]) inv.getArguments()[2];//new Object[]{data, attachment}

			try {

				Object requestDTO = args[0];//JSONString
				Object requestAttachment = args[1];//Map<String,Object>

				Method method = findMethodByMethodSignature(invoker.getInterface(), methodName, requestDTO);

				Class<?>[] params = method.getParameterTypes();//never null


				if (requestAttachment != null) {
					Map<String, String> attachment = (Map<String, String>) requestAttachment;
					RpcInvocation rpcInvocation = (RpcInvocation) inv;
					rpcInvocation.addAttachments(attachment);
				}

				if (params.length > 1) {
					//facade accept only one parameter
					return invoker.invoke(inv);
				}


				if (requestDTO == null && params.length == 0) {
					//无参数接口

				}

				if (requestDTO != null && params.length == 1) {
					//仅一个参数的接口 e.g. hello(XDTO xDTO)
					String json = (String) args[0];
					args[0] = com.alibaba.fastjson.JSON.parseObject(json, params[0]);

				}

				Result result = invoker.invoke(new RpcInvocation(method, new Object[]{args[0]}, inv.getAttachments()));


				if (result.hasException()
						&& !(result.getException() instanceof GenericException)) {
					return new RpcResult(new GenericException(result.getException()));
				}
				if (result instanceof RpcResult && result.getValue() != null) {
					//facade serialization pair up
					((RpcResult) result).setValue(com.alibaba.fastjson.JSON.toJSON(result.getValue()));
				}
				return result;

			} catch (NoSuchMethodException e) {
				throw new RpcException(e.getMessage(), e);
			} catch (ClassNotFoundException e) {
				throw new RpcException(e.getMessage(), e);
			} catch (RuntimeException e) {
				throw new RpcException(e.getMessage(), e);
			}
		}
		return invoker.invoke(inv);
	}

	private static final ConcurrentMap<String, Method> Signature_METHODS_CACHE = new ConcurrentHashMap<String, Method>();

	private Method findMethodByMethodSignature(Class<?> clazz, String methodName, Object requestDTO)
			throws NoSuchMethodException, ClassNotFoundException {
		Method method = Signature_METHODS_CACHE.get(clazz.getSimpleName() + "." + methodName);
		if (method != null) {
			return method;
		}
		List<Method> finded = new ArrayList<Method>();
		for (Method m : clazz.getMethods()) {
			if (m.getName().equals(methodName)) {
				Class<?>[] classes = m.getParameterTypes();
				if (classes.length == 1) {
					if (!classes[0].isPrimitive()) {
						//参数只有一个 并且不是基本类型
						finded.add(m);
					} else {
						logger.warn("primitive arg need wrap by dto , method : {}", m.getName());
						//基本类型需要用DTO封装
						continue;
					}
				} else if (classes.length == 0) {
					//无参数方法
					if (requestDTO == null) {
						//传入参数为空
						finded.add(m);
					} else {
						if (requestDTO instanceof String) {
							//传入参数为空字符串
							if (StringUtils.isBlank((String) requestDTO) ||
									//兼容传null字符串的调用
									"null".equalsIgnoreCase((String) requestDTO)) {
								finded.add(m);
							} else {
								//需要传入空字符串
								logger.warn("method accept only null or empty param, method : {} , params : {}", m.getName(),requestDTO);
								continue;
							}
						} else {

							logger.warn("method accept only null or empty param but get not String not null , method : {} , params : {}", m.getName(), requestDTO);
							continue;
						}
					}
				} else {
					//多参数暂时无法支持
					logger.warn("method accept more than one params please change method args to dto , method : {} ", m.getName());
					continue;
				}
			}
		}
		if (finded.isEmpty()) {
			throw new NoSuchMethodException("No such method " + methodName + " in class " + clazz);
		}
		if (finded.size() > 1) {
			String msg = String.format("Not unique method for method name(%s) in class(%s), find %d methods.",
					methodName, clazz.getName(), finded.size());
			throw new IllegalStateException(msg);
		}
		method = finded.get(0);

		Signature_METHODS_CACHE.put(clazz.getSimpleName() + "." + methodName, method);
		return method;
	}

}
