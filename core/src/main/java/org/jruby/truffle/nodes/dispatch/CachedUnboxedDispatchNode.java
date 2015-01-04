/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.dispatch;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import org.jruby.truffle.runtime.LexicalScope;
import org.jruby.truffle.runtime.RubyArguments;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.core.RubyBasicObject;
import org.jruby.truffle.runtime.core.RubyProc;
import org.jruby.truffle.runtime.methods.RubyMethod;

public abstract class CachedUnboxedDispatchNode extends CachedDispatchNode {

    private final Class expectedClass;
    private final Assumption unmodifiedAssumption;

    private final Object value;

    private final RubyMethod method;
    @Child protected DirectCallNode callNode;
    @Child protected IndirectCallNode indirectCallNode;

    public CachedUnboxedDispatchNode(RubyContext context, Object cachedName, DispatchNode next,
                                     Class expectedClass, Assumption unmodifiedAssumption, Object value,
                                     RubyMethod method, boolean indirect) {
        super(context, cachedName, next, indirect);
        this.expectedClass = expectedClass;
        this.unmodifiedAssumption = unmodifiedAssumption;
        this.value = value;
        this.method = method;

        if (method != null) {
            if (indirect) {
                indirectCallNode = Truffle.getRuntime().createIndirectCallNode();
            } else {
                callNode = Truffle.getRuntime().createDirectCallNode(method.getCallTarget());
            }
        }
    }

    public CachedUnboxedDispatchNode(CachedUnboxedDispatchNode prev) {
        super(prev);
        expectedClass = prev.expectedClass;
        unmodifiedAssumption = prev.unmodifiedAssumption;
        value = prev.value;
        method = prev.method;
        callNode = prev.callNode;
        indirectCallNode = prev.indirectCallNode;
    }

    @Specialization(guards = {"isPrimitive", "guardName"})
    public Object dispatch(
            VirtualFrame frame,
            LexicalScope lexicalScope,
            Object receiverObject,
            Object methodName,
            Object blockObject,
            Object argumentsObjects,
            Dispatch.DispatchAction dispatchAction) {
        CompilerAsserts.compilationConstant(dispatchAction);

        // Check the class is what we expect

        if (receiverObject.getClass() != expectedClass) {
            return next.executeDispatch(
                    frame,
                    lexicalScope,
                    receiverObject,
                    methodName,
                    blockObject,
                    argumentsObjects,
                    dispatchAction);
        }

        // Check the class has not been modified

        try {
            unmodifiedAssumption.check();
        } catch (InvalidAssumptionException e) {
            return resetAndDispatch(
                    frame,
                    lexicalScope,
                    receiverObject,
                    methodName,
                    CompilerDirectives.unsafeCast(blockObject, RubyProc.class, true, false),
                    argumentsObjects,
                    dispatchAction,
                    "class modified");
        }

        if (dispatchAction == Dispatch.DispatchAction.CALL_METHOD) {
            if (isIndirect()) {
                return indirectCallNode.call(
                        frame,
                        method.getCallTarget(),
                        RubyArguments.pack(
                                method,
                                method.getDeclarationFrame(),
                                receiverObject, CompilerDirectives.unsafeCast(blockObject, RubyProc.class, true, false),
                                CompilerDirectives.unsafeCast(argumentsObjects, Object[].class, true)));
            } else {
                return callNode.call(
                        frame,
                        RubyArguments.pack(
                                method,
                                method.getDeclarationFrame(),
                                receiverObject, CompilerDirectives.unsafeCast(blockObject, RubyProc.class, true, false),
                                CompilerDirectives.unsafeCast(argumentsObjects, Object[].class, true)));
            }
        } else  if (dispatchAction == Dispatch.DispatchAction.RESPOND_TO_METHOD) {
            return true;
        } else if (dispatchAction == Dispatch.DispatchAction.READ_CONSTANT) {
            return value;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Fallback
    public Object dispatchFallback(
            VirtualFrame frame,
            LexicalScope lexicalScope,
            Object receiverObject,
            Object methodName,
            Object blockObject,
            Object argumentsObjects,
            Dispatch.DispatchAction dispatchAction) {
        return next.executeDispatch(
                frame,
                lexicalScope,
                receiverObject,
                methodName,
                CompilerDirectives.unsafeCast(blockObject, RubyProc.class, true, false),
                argumentsObjects,
                dispatchAction);
    }

    protected static final boolean isPrimitive(
            LexicalScope lexicalScope,
            Object receiverObject,
            Object methodName,
            Object blockObject,
            Object argumentsObjects) {
        return !(receiverObject instanceof RubyBasicObject);
    }

    public RubyMethod getMethod() {
        return method;
    }

}
