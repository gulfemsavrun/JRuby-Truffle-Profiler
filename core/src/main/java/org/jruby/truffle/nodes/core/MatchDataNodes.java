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
import org.jruby.truffle.runtime.*;
import org.jruby.truffle.runtime.core.*;
import org.jruby.truffle.runtime.core.RubyArray;

@CoreClass(name = "MatchData")
public abstract class MatchDataNodes {

    @CoreMethod(names = "[]", minArgs = 1, maxArgs = 1, lowerFixnumParameters = 0)
    public abstract static class GetIndexNode extends CoreMethodNode {

        public GetIndexNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GetIndexNode(GetIndexNode prev) {
            super(prev);
        }

        @Specialization
        public Object getIndex(RubyMatchData matchData, int index) {
            notDesignedForCompilation();

            if (index >= matchData.getValues().length) {
                return NilPlaceholder.INSTANCE;
            } else {
                return matchData.getValues()[index];
            }
        }

    }

    @CoreMethod(names = {"to_a", "captures"}, maxArgs = 0)
    public abstract static class ToANode extends CoreMethodNode {

        public ToANode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ToANode(ToANode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray toA(RubyMatchData matchData) {
            notDesignedForCompilation();

            return RubyArray.fromObjects(getContext().getCoreLibrary().getArrayClass(), matchData.getValues());
        }

    }

    @CoreMethod(names = "values_at", isSplatted = true)
    public abstract static class ValuesAtNode extends CoreMethodNode {

        public ValuesAtNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ValuesAtNode(ValuesAtNode prev) {
            super(prev);
        }

        @Specialization
        public RubyArray valuesAt(RubyMatchData matchData, Object[] args) {
            notDesignedForCompilation();

            final int[] indicies = new int[args.length];

            for (int n = 0; n < args.length; n++) {
                indicies[n] = RubyFixnum.toInt(args[n]);
            }

            return RubyArray.fromObjects(getContext().getCoreLibrary().getArrayClass(), matchData.valuesAt(indicies));
        }

    }

}
