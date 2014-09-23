/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.methods;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

import org.jruby.runtime.Visibility;
import org.jruby.truffle.nodes.*;
import org.jruby.truffle.nodes.profiler.ProfilerTranslator;
import org.jruby.truffle.runtime.*;
import org.jruby.truffle.runtime.core.*;
import org.jruby.truffle.runtime.methods.*;
import org.jruby.util.cli.Options;

/**
 * Define a method. That is, store the definition of a method and when executed
 * produce the executable object that results.
 */
public class MethodDefinitionNode extends RubyNode {

    protected final String name;
    protected final SharedMethodInfo sharedMethodInfo;

    protected final RubyRootNode rootNode;

    protected final boolean requiresDeclarationFrame;

    protected final boolean ignoreLocalVisibility;

    public MethodDefinitionNode(RubyContext context, SourceSection sourceSection, String name, SharedMethodInfo sharedMethodInfo,
            boolean requiresDeclarationFrame, RubyRootNode rootNode, boolean ignoreLocalVisibility) {
        super(context, sourceSection);
        this.name = name;
        this.sharedMethodInfo = sharedMethodInfo;
        this.requiresDeclarationFrame = requiresDeclarationFrame;
        this.rootNode = rootNode;
        this.ignoreLocalVisibility = ignoreLocalVisibility;
    }

    public RubyMethod executeMethod(VirtualFrame frame) {
        notDesignedForCompilation();

        final MaterializedFrame declarationFrame;

        if (requiresDeclarationFrame) {
            declarationFrame = frame.materialize();
        } else {
            declarationFrame = null;
        }

        Visibility visibility;

        if (ignoreLocalVisibility) {
            visibility = Visibility.PUBLIC;
        } else if (name.equals("initialize") || name.equals("initialize_copy") || name.equals("initialize_clone") || name.equals("initialize_dup") || name.equals("respond_to_missing?")) {
            visibility = Visibility.PRIVATE;
        } else {
            final FrameSlot visibilitySlot = frame.getFrameDescriptor().findFrameSlot(RubyModule.VISIBILITY_FRAME_SLOT_ID);

            if (visibilitySlot == null) {
                visibility = Visibility.PUBLIC;
            } else {
                Object visibilityObject;

                try {
                    visibilityObject = frame.getObject(visibilitySlot);
                } catch (FrameSlotTypeException e) {
                    throw new RuntimeException(e);
                }

                if (visibilityObject instanceof Visibility) {
                    visibility = (Visibility) visibilityObject;
                } else {
                    visibility = Visibility.PUBLIC;
                }
            }
        }

        final RubyRootNode rootNodeClone = NodeUtil.cloneNode(rootNode);
        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNodeClone);
        RubyMethod rubyMethod = new RubyMethod(sharedMethodInfo, name, null, visibility, false, callTarget, declarationFrame);

        /**
         * When a profiler related option is enabled, {@link ProfilerTranslator} traverses the method to create {@link RubyWrapper} wrapper nodes.
         */
        if (Options.TRUFFLE_PROFILE_CALLS.load()
                || Options.TRUFFLE_PROFILE_CONTROL_FLOW.load()
                || Options.TRUFFLE_PROFILE_VARIABLE_ACCESSES.load()
                || Options.TRUFFLE_PROFILE_OPERATIONS.load()
                || Options.TRUFFLE_PROFILE_COLLECTION_OPERATIONS.load()) {
            ProfilerTranslator profilerTranslator = ProfilerTranslator.getInstance();
            profilerTranslator.translate(rootNodeClone, false, false, rubyMethod);
        }

        return rubyMethod;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeMethod(frame);
    }

    public String getName() {
        return name;
    }

    public RubyRootNode getMethodRootNode() {
        return rootNode;
    }

}
