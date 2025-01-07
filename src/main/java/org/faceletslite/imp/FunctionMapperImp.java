package org.faceletslite.imp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.el.FunctionMapper;

public class FunctionMapperImp extends FunctionMapper 
{
	private final Map<String,List<Class<?>>> classesByPrefix;
	private final Map<String,Method> methodByPrefixAndName = new ConcurrentHashMap<>();

	public FunctionMapperImp(Map<String, List<Class<?>>> classesByPrefix)
	{
		this.classesByPrefix = classesByPrefix;
	}
	
	@Override
	public Method resolveFunction(String prefix, String name) {
		String key = prefix+":"+name;
		Method result = methodByPrefixAndName.get(key);
		if (result==null) {
			List<Class<?>> classes = classesByPrefix.get(prefix);
			if (classes==null) {
				return null;
			}
			List<Method> matches = new ArrayList<>();
			for (Class<?> clazz: classes) {
				for (Method method: clazz.getMethods()) {
					if (method.getName().equals(name)) {
						matches.add(method);
					}
				}
			}
			if (matches.size()>1) {
				throw new RuntimeException("found multiple methods '"+name+"' for prefix "+prefix);
			}
			if (matches.size()==0) {
				return null;
			}
			result = matches.get(0);
			methodByPrefixAndName.put(key, result);
		}
		return result;
	}
}
