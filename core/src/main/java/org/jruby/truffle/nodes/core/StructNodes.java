/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.jruby.truffle.nodes.yield.YieldDispatchHeadNode;
import org.jruby.truffle.runtime.*;
import org.jruby.truffle.runtime.core.*;

@CoreClass(name = "Struct")
public abstract class StructNodes {

    @CoreMethod(names = "initialize", needsBlock = true, isSplatted = true)
    public abstract static class InitalizeNode extends CoreMethodNode {

        @Child protected YieldDispatchHeadNode yield;

        public InitalizeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            yield = new YieldDispatchHeadNode(context);
        }

        public InitalizeNode(InitalizeNode prev) {
            super(prev);
            yield = prev.yield;
        }

        @Specialization
        public NilPlaceholder initialize(RubyClass struct, Object[] args, NilPlaceholder block) {
            notDesignedForCompilation();

            final RubySymbol[] symbols = new RubySymbol[args.length];

            for (int n = 0; n < args.length; n++) {
                symbols[n] = (RubySymbol) args[n];
            }

            for (RubySymbol symbol : symbols) {
                ModuleNodes.AttrAccessorNode.attrAccessor(this, getContext(), Truffle.getRuntime().getCallerFrame().getCallNode().getEncapsulatingSourceSection(), struct, symbol.toString());
            }

            return NilPlaceholder.INSTANCE;
        }

        @Specialization
        public NilPlaceholder initialize(VirtualFrame frame, RubyClass struct, Object[] args, RubyProc block) {
            notDesignedForCompilation();

            final RubySymbol[] symbols = new RubySymbol[args.length];

            for (int n = 0; n < args.length; n++) {
                symbols[n] = (RubySymbol) args[n];
            }

            for (RubySymbol symbol : symbols) {
                ModuleNodes.AttrAccessorNode.attrAccessor(this, getContext(), Truffle.getRuntime().getCallerFrame().getCallNode().getEncapsulatingSourceSection(), struct, symbol.toString());
            }

            yield.dispatchWithModifiedSelf(frame, block, struct);

            return NilPlaceholder.INSTANCE;
        }

    }

}
