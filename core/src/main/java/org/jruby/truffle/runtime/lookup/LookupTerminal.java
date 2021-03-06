/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.runtime.lookup;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.runtime.core.RubyModule;
import org.jruby.truffle.runtime.methods.*;

/**
 * A terminal in the lookup graph.
 */
public class LookupTerminal implements LookupNode {

    public static final LookupTerminal INSTANCE = new LookupTerminal();

    public boolean setClassVariableIfAlreadySet(RubyNode currentNode, String variableName, Object value) {
        return false;
    }

    @Override
    public RubyModule.RubyConstant lookupConstant(String constantName) {
        return null;
    }

    @Override
    public Object lookupClassVariable(String constantName) {
        return null;
    }

    @Override
    public RubyMethod lookupMethod(String methodName) {
        return null;
    }

    @Override
    public Assumption getUnmodifiedAssumption() {
        return AlwaysValidAssumption.INSTANCE;
    }

    public Set<String> getClassVariables() {
        return Collections.emptySet();
    }

    public void getMethods(Map<String, RubyMethod> methods) { }

    @Override
    public boolean chainContains(LookupNode node) {
        // checking if a LookupNode chain contains a LookupTerminal makes no sense, so we omit "return node == INSTANCE"
        return false;
    }
}
