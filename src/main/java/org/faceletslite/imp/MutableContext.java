package org.faceletslite.imp;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.FunctionMapper;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

class MutableContext extends ELContext {

    private final FaceletsCompilerImp compiler;
    private final ELContext fallback;
    private Object scope;
    private Object environmentVars;
    private boolean suspended;
    private final Map<String, ValueExpression> variables = new LinkedHashMap<String, ValueExpression>();
    private final VariableMapper variableMapper = new VariableMapper() {

        @Override
        public ValueExpression setVariable(String name, ValueExpression expr) {
            variables.put(name, expr);
            return expr;
        }

        @Override
        public ValueExpression resolveVariable(String name) {
            ELResolver resolver = compiler.resolver;
            Object value = scope == null ? null : resolver.getValue(MutableContext.this, scope, name);
            if (value != null) {
                return wrap(value);
            }
            value = environmentVars == null ? null : resolver.getValue(MutableContext.this, environmentVars, name);
            if (value != null) {
                return wrap(value);
            }
            ValueExpression expr = variables.get(name);
            if (expr != null) {
                return expr;
            }
            return fallback == null ? wrap(null) : fallback.getVariableMapper().resolveVariable(name);
        }
    };

    MutableContext(FaceletsCompilerImp compiler) {
        this(compiler, null);
    }

    MutableContext(FaceletsCompilerImp compiler, ELContext fallback) {
        this.compiler = compiler;
        this.fallback = fallback;
        this.suspended = (fallback instanceof MutableContext) ? ((MutableContext) fallback).suspended : false;
    }

    MutableContext nest() {
        return new MutableContext(compiler, this);
    }

    MutableContext scope(Object scope) {
        this.scope = scope;
        return this;
    }

    MutableContext suspend(boolean suspend) {
        this.suspended = suspend;
        return this;
    }

    MutableContext put(String name, Object value) {
        if (name != null) {
            variableMapper.setVariable(name, wrap(value));
        }
        return this;
    }

    private ValueExpression wrap(Object value) {
        return compiler.expressionFactory.createValueExpression(value, Object.class);
    }

    @Override
    public ELResolver getELResolver() {
        return compiler.resolver;
    }

    @Override
    public FunctionMapper getFunctionMapper() {
        return compiler.functionMapper;
    }

    @Override
    public VariableMapper getVariableMapper() {
        return variableMapper;
    }

    @SuppressWarnings("unchecked")
    <T> T eval(String text, Class<?> clazz, Object environmentVars) {
        if (suspended) {
            if (clazz == String.class || clazz == Object.class) {
                return (T) text;
            }
        }
        this.environmentVars = environmentVars;
        return (T) compiler.expressionFactory
            .createValueExpression(this, text, clazz)
            .getValue(this);
    }
}
