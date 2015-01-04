/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.methods;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import org.jruby.runtime.Visibility;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.nodes.profiler.ProfilerTranslator;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.core.RubyModule;
import org.jruby.truffle.runtime.methods.RubyMethod;
import org.jruby.truffle.runtime.methods.SharedMethodInfo;
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

        return executeMethod(frame, declarationFrame);
    }

    public RubyMethod executeMethod(VirtualFrame frame, MaterializedFrame declarationFrame) {
        notDesignedForCompilation();

        Visibility visibility = getVisibility(frame);

        final RubyRootNode rootNodeClone = NodeUtil.cloneNode(rootNode);
        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNodeClone);

        final RubyMethod method = new RubyMethod(sharedMethodInfo, name, null, visibility, false, callTarget, declarationFrame);

        /**
         * When a profiler related option is enabled, {@link ProfilerTranslator} traverses the method to create {@link RubyWrapper} wrapper nodes.
         */
        if (Options.TRUFFLE_PROFILE_CALLS.load()
                || Options.TRUFFLE_PROFILE_CONTROL_FLOW.load()
                || Options.TRUFFLE_PROFILE_VARIABLE_ACCESSES.load()
                || Options.TRUFFLE_PROFILE_OPERATIONS.load()
                || Options.TRUFFLE_PROFILE_COLLECTION_OPERATIONS.load()) {
            ProfilerTranslator profilerTranslator = ProfilerTranslator.getInstance();
            profilerTranslator.translate(rootNodeClone, false, false, method);
        }

        return method;
    }

    private Visibility getVisibility(VirtualFrame frame) {
        if (ignoreLocalVisibility) {
            return Visibility.PUBLIC;
        } else if (name.equals("initialize") || name.equals("initialize_copy") || name.equals("initialize_clone") || name.equals("initialize_dup") || name.equals("respond_to_missing?")) {
            return Visibility.PRIVATE;
        } else {
            final FrameSlot visibilitySlot = frame.getFrameDescriptor().findFrameSlot(RubyModule.VISIBILITY_FRAME_SLOT_ID);

            if (visibilitySlot == null) {
                return Visibility.PUBLIC;
            } else {
                Object visibilityObject;

                try {
                    visibilityObject = frame.getObject(visibilitySlot);
                } catch (FrameSlotTypeException e) {
                    throw new RuntimeException(e);
                }

                if (visibilityObject instanceof Visibility) {
                    return  (Visibility) visibilityObject;
                } else {
                    return Visibility.PUBLIC;
                }
            }
        }
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

    public SharedMethodInfo getSharedMethodInfo() {
        return sharedMethodInfo;
    }
}
