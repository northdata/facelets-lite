package org.faceletslite.test;


import java.beans.FeatureDescriptor;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;

import org.json.JSONArray;
import org.json.JSONObject;


public class JsonElResolver extends ELResolver
{
	@Override
    public Object getValue(ELContext elContext, Object base, Object property)
    {
        if (property!=null && base!=null)
        {
	        if (base instanceof JSONObject)
	        {
	            elContext.setPropertyResolved(true);
	        	return transformResult(((JSONObject)base).opt(property.toString()));
	        }
	        
	        if ((base instanceof JSONArray) && (property instanceof Number))
	        {
	            elContext.setPropertyResolved(true);
	        	return transformResult(((JSONArray)base).opt(((Number)property).intValue()));
	        }
        }
        return null;
    }
    
    @Override
    public Class<?> getType(ELContext elContext, Object base, Object property) 
    {
    	return null;
    }

    @Override
    public boolean isReadOnly(ELContext elContext, Object base, Object property) 
    {
        return true;
    }

    @Override
    public void setValue(ELContext elContext, Object base, Object property,
            Object value) 
    {
    }
    
    @Override
    public Class<?> getCommonPropertyType(ELContext elContext, Object base) 
    {
        return Object.class;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext elContext, Object base)
    {
        return Collections.emptyIterator();
    }
    
    
    private Object transformResult(Object object)
    {
    	if (object!=null) {
	    	// this allows iterating over json array with <c:forEach/>
    		if (object instanceof JSONArray)
	    	{
	    		return new JsonArrayList(((JSONArray)object));
	    	}
    	}
    	return object;
    }
    
    public static class JsonArrayList extends AbstractList<Object>
    {
    	private final JSONArray array;
    	
    	public JsonArrayList(JSONArray array) {
    		this.array = array;
    	}

    	@Override
    	public Object get(int index) {
    		return array.opt(index);
    	}

    	@Override
    	public int size() {
    		return array.length();
    	}
    }
        
}

