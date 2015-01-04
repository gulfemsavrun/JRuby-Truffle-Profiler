/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.control;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.BranchProfile;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.cast.BooleanCastNode;
import org.jruby.truffle.runtime.RubyContext;

/**
 * Represents a Ruby {@code if} expression. Note that in this representation we always have an
 * {@code else} part.
 */
public class IfNode extends RubyNode {

    @Child protected BooleanCastNode condition;
    @Child protected RubyNode thenBody;
    @Child protected RubyNode elseBody;

    private final BranchProfile thenProfile = BranchProfile.create();
    private final BranchProfile elseProfile = BranchProfile.create();

    @CompilerDirectives.CompilationFinal private int thenCount;
    @CompilerDirectives.CompilationFinal private int elseCount;

    public IfNode(RubyContext context, SourceSection sourceSection, BooleanCastNode condition, RubyNode thenBody, RubyNode elseBody) {
        super(context, sourceSection);

        assert condition != null;
        assert thenBody != null;
        assert elseBody != null;

        this.condition = condition;
        this.thenBody = thenBody;
        this.elseBody = elseBody;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (CompilerDirectives.injectBranchProbability(getBranchProbability(), condition.executeBoolean(frame))) {
            if (CompilerDirectives.inInterpreter()) {
                thenCount++;
            }
            thenProfile.enter();
            return thenBody.execute(frame);
        } else {
            if (CompilerDirectives.inInterpreter()) {
                elseCount++;
            }
            elseProfile.enter();
            return elseBody.execute(frame);
        }
    }

    private double getBranchProbability() {
        final int totalCount = thenCount + elseCount;

        if (totalCount == 0) {
            return 0;
        } else {
            return (double) thenCount / (double) (thenCount + elseCount);
        }
    }

    public RubyNode getThen() {
        return thenBody;
    }

    public RubyNode getElse() {
        return elseBody;
    }
}
