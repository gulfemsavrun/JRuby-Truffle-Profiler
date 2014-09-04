/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.objects;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.utilities.BranchProfile;

import org.jruby.truffle.nodes.*;
import org.jruby.truffle.nodes.cast.BoxingNode;
import org.jruby.truffle.nodes.objectstorage.ReadHeadObjectFieldNode;
import org.jruby.truffle.nodes.objectstorage.ReadObjectFieldNode;
import org.jruby.truffle.nodes.objectstorage.RespecializeHook;
import org.jruby.truffle.nodes.objectstorage.UninitializedReadObjectFieldNode;
import org.jruby.truffle.runtime.*;
import org.jruby.truffle.runtime.core.RubyBasicObject;
import org.jruby.truffle.runtime.objectstorage.*;

@NodeInfo(shortName = "read_instance")
public class ReadInstanceVariableNode extends RubyNode implements ReadNode {

    private final RespecializeHook hook = new RespecializeHook() {

        @Override
        public void hookRead(ObjectStorage object, String name) {
            final RubyBasicObject rubyObject = (RubyBasicObject) object;

            if (!rubyObject.hasPrivateLayout()) {
                rubyObject.updateLayoutToMatchClass();
            }
        }

        @Override
        public void hookWrite(ObjectStorage object, String name, Object value) {
            final RubyBasicObject rubyObject = (RubyBasicObject) object;

            if (!rubyObject.hasPrivateLayout()) {
                rubyObject.updateLayoutToMatchClass();
            }

            rubyObject.setInstanceVariable(name, value);
        }

    };

    @Child protected BoxingNode receiver;
    @Child protected ReadHeadObjectFieldNode readNode;
    private final boolean isGlobal;

    private final BranchProfile nullProfile = new BranchProfile();

    public ReadInstanceVariableNode(RubyContext context, SourceSection sourceSection, String name, RubyNode receiver, boolean isGlobal) {
        super(context, sourceSection);
        this.receiver = new BoxingNode(context, sourceSection, receiver);
        readNode = new ReadHeadObjectFieldNode(name, hook);
        this.isGlobal = isGlobal;
    }

    @Override
    public int executeIntegerFixnum(VirtualFrame frame) throws UnexpectedResultException {
        return readNode.executeInteger(receiver.executeRubyBasicObject(frame));
    }

    @Override
    public long executeLongFixnum(VirtualFrame frame) throws UnexpectedResultException {
        return readNode.executeLong(receiver.executeRubyBasicObject(frame));
    }

    @Override
    public double executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        return readNode.executeDouble(receiver.executeRubyBasicObject(frame));
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = readNode.execute(receiver.executeRubyBasicObject(frame));

        if (value == null) {
            nullProfile.enter();
            value = NilPlaceholder.INSTANCE;
        }

        return value;
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        notDesignedForCompilation();

        if (isGlobal) {
            if (readNode.getName().equals("$~") || readNode.isSet(receiver.executeRubyBasicObject(frame))) {
                return getContext().makeString("global-variable");
            } else{
                return NilPlaceholder.INSTANCE;
            }
        }

        final RubyContext context = getContext();

        try {
            final Object receiverObject = receiver.execute(frame);
            final RubyBasicObject receiverRubyObject = context.getCoreLibrary().box(receiverObject);

            final ObjectLayout layout = receiverRubyObject.getObjectLayout();
            final StorageLocation storageLocation = layout.findStorageLocation(readNode.getName());

            if (storageLocation.isSet(receiverRubyObject)) {
                return context.makeString("instance-variable");
            } else {
                return NilPlaceholder.INSTANCE;
            }
        } catch (Exception e) {
            return NilPlaceholder.INSTANCE;
        }
    }

    @Override
    public RubyNode makeWriteNode(RubyNode rhs) {
        return new WriteInstanceVariableNode(getContext(), getSourceSection(), readNode.getName(), receiver, rhs, isGlobal);
    }
}
