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

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import org.jruby.runtime.Visibility;
import org.jruby.truffle.nodes.CoreSourceSection;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.nodes.control.SequenceNode;
import org.jruby.truffle.nodes.debug.RubyWrapper;
import org.jruby.truffle.nodes.methods.ExceptionTranslatingNode;
import org.jruby.truffle.nodes.methods.arguments.*;
import org.jruby.truffle.nodes.objects.SelfNode;
import org.jruby.truffle.nodes.profiler.ProfilerProber;
import org.jruby.truffle.runtime.util.ArrayUtils;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.UndefinedPlaceholder;
import org.jruby.truffle.runtime.core.RubyClass;
import org.jruby.truffle.runtime.core.RubyModule;
import org.jruby.truffle.runtime.methods.Arity;
import org.jruby.truffle.runtime.methods.RubyMethod;
import org.jruby.truffle.runtime.methods.SharedMethodInfo;
import org.jruby.util.cli.Options;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class CoreMethodNodeManager {

    public static void addStandardMethods(RubyClass rubyObjectClass) {
        for (MethodDetails methodDetails : getMethods()) {
            addMethod(rubyObjectClass, methodDetails);
        }
    }

    public static void addExtensionMethods(RubyClass rubyObjectClass, List<? extends NodeFactory<? extends CoreMethodNode>> nodeFactories) {
        final List<MethodDetails> methods = new ArrayList<>();
        getMethods(methods, nodeFactories);

        for (MethodDetails methodDetails : methods) {
            addMethod(rubyObjectClass, methodDetails);
        }
    }

    /**
     * Collect up all the core method nodes. Abstracted to allow the SVM to implement at compile
     * type.
     */
    public static List<MethodDetails> getMethods() {
        final List<MethodDetails> methods = new ArrayList<>();
        getMethods(methods, ArrayNodesFactory.getFactories());
        getMethods(methods, BasicObjectNodesFactory.getFactories());
        getMethods(methods, BindingNodesFactory.getFactories());
        getMethods(methods, BignumNodesFactory.getFactories());
        getMethods(methods, ClassNodesFactory.getFactories());
        getMethods(methods, ContinuationNodesFactory.getFactories());
        getMethods(methods, ComparableNodesFactory.getFactories());
        getMethods(methods, DirNodesFactory.getFactories());
        getMethods(methods, ExceptionNodesFactory.getFactories());
        getMethods(methods, FalseClassNodesFactory.getFactories());
        getMethods(methods, FiberNodesFactory.getFactories());
        getMethods(methods, FileNodesFactory.getFactories());
        getMethods(methods, FixnumNodesFactory.getFactories());
        getMethods(methods, FloatNodesFactory.getFactories());
        getMethods(methods, HashNodesFactory.getFactories());
        getMethods(methods, KernelNodesFactory.getFactories());
        getMethods(methods, MainNodesFactory.getFactories());
        getMethods(methods, MatchDataNodesFactory.getFactories());
        getMethods(methods, MathNodesFactory.getFactories());
        getMethods(methods, ModuleNodesFactory.getFactories());
        getMethods(methods, NilClassNodesFactory.getFactories());
        getMethods(methods, ObjectNodesFactory.getFactories());
        getMethods(methods, ObjectSpaceNodesFactory.getFactories());
        getMethods(methods, ProcessNodesFactory.getFactories());
        getMethods(methods, ProcNodesFactory.getFactories());
        getMethods(methods, RangeNodesFactory.getFactories());
        getMethods(methods, RegexpNodesFactory.getFactories());
        getMethods(methods, SignalNodesFactory.getFactories());
        getMethods(methods, StringNodesFactory.getFactories());
        getMethods(methods, StructNodesFactory.getFactories());
        getMethods(methods, SymbolNodesFactory.getFactories());
        getMethods(methods, ThreadNodesFactory.getFactories());
        getMethods(methods, TimeNodesFactory.getFactories());
        getMethods(methods, TrueClassNodesFactory.getFactories());
        getMethods(methods, TruffleDebugNodesFactory.getFactories());
        getMethods(methods, EncodingNodesFactory.getFactories());
        return methods;
    }

    /**
     * Collect up the core methods created by a factory.
     */
    private static void getMethods(List<MethodDetails> methods, List<? extends NodeFactory<? extends CoreMethodNode>> nodeFactories) {
        for (NodeFactory<? extends RubyNode> nodeFactory : nodeFactories) {
            final GeneratedBy generatedBy = nodeFactory.getClass().getAnnotation(GeneratedBy.class);
            final Class<?> nodeClass = generatedBy.value();
            final CoreClass classAnnotation = nodeClass.getEnclosingClass().getAnnotation(CoreClass.class);
            final CoreMethod methodAnnotation = nodeClass.getAnnotation(CoreMethod.class);

            if (methodAnnotation != null) {
                methods.add(new MethodDetails(classAnnotation, methodAnnotation, nodeFactory));
            }
        }
    }

    private static void addMethods(RubyClass rubyObjectClass, List<MethodDetails> methods) {
        for (MethodDetails methodDetails : methods) {
            addMethod(rubyObjectClass, methodDetails);
        }
    }

    private static int index = 1;

    private static void addMethod(RubyClass rubyObjectClass, MethodDetails methodDetails) {
        assert rubyObjectClass != null;
        assert methodDetails != null;

        final RubyContext context = rubyObjectClass.getContext();

        RubyModule module;

        if (methodDetails.getClassAnnotation().name().equals("main")) {
            module = context.getCoreLibrary().getMainObject().getSingletonClass(null);
        } else {
            module = (RubyModule) rubyObjectClass.lookupConstant(methodDetails.getClassAnnotation().name()).value;
        }

        assert module != null : methodDetails.getClassAnnotation().name();

        final List<String> names = Arrays.asList(methodDetails.getMethodAnnotation().names());
        assert names.size() >= 1;

        final String canonicalName = names.get(0);
        final List<String> aliases = names.subList(1, names.size());

        final Visibility visibility = methodDetails.getMethodAnnotation().visibility();

        final RubyRootNode rootNode = makeGenericMethod(context, methodDetails);

        final RubyMethod method = new RubyMethod(rootNode.getSharedMethodInfo(), canonicalName, module, visibility, false,
                Truffle.getRuntime().createCallTarget(rootNode), null);

        /**
         * TODO
         * Builtin core source sections do not have actual source sections, they are basically null source sections
         * To profile builtins, dummy source sections from a builtins file are assigned.
         * It's better to come up with a better implementation
         */
        if (Options.TRUFFLE_PROFILE_BUILTIN_CALLS.load()) {
            Source source = null;
            try {
                source = Source.fromFileName("/Users/gsy/Projects/JRuby-Truffle-Profiler/bench/truffle/builtins.rb");
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (rootNode.getBody() != null) {
                if (rootNode.getBody().getSourceSection() != null && rootNode.getBody().getSourceSection() instanceof CoreSourceSection) {
                    SourceSection sourceSection = source.createSection("builtin-in", index);
                    rootNode.getBody().clearSourceSection();
                    rootNode.getBody().assignSourceSection(sourceSection);
                    RubyWrapper wrapperNode = null;
                    wrapperNode = ProfilerProber.getInstance().probeAsMethodBody(rootNode.getBody(), method);
                    rootNode.getBody() .replace(wrapperNode);
                    index++;
                }
            }
        }

        module.addMethod(null, method);

        if (methodDetails.getMethodAnnotation().isModuleMethod()) {
            module.getSingletonClass(null).addMethod(null, method);
        }

        for (String alias : aliases) {
            final RubyMethod withAlias = method.withNewName(alias);

            module.addMethod(null, withAlias);

            if (methodDetails.getMethodAnnotation().isModuleMethod()) {
                module.getSingletonClass(null).addMethod(null, withAlias);
            }
        }
    }

    private static RubyRootNode makeGenericMethod(RubyContext context, MethodDetails methodDetails) {
        final CoreSourceSection sourceSection = new CoreSourceSection(methodDetails.getClassAnnotation().name(), methodDetails.getMethodAnnotation().names()[0]);

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(sourceSection, methodDetails.getIndicativeName(), false, null);

        final Arity arity = new Arity(methodDetails.getMethodAnnotation().minArgs(), methodDetails.getMethodAnnotation().maxArgs());

        final List<RubyNode> argumentsNodes = new ArrayList<>();

        if (methodDetails.getMethodAnnotation().needsSelf()) {
            RubyNode readSelfNode = new SelfNode(context, sourceSection);

            if (methodDetails.getMethodAnnotation().lowerFixnumSelf()) {
                readSelfNode = new FixnumLowerNode(readSelfNode);
            }

            argumentsNodes.add(readSelfNode);
        }

        if (methodDetails.getMethodAnnotation().isSplatted()) {
            argumentsNodes.add(new ReadAllArgumentsNode(context, sourceSection));
        } else {
            assert arity.getMaximum() != Arity.NO_MAXIMUM;

            for (int n = 0; n < arity.getMaximum(); n++) {
                RubyNode readArgumentNode = new ReadPreArgumentNode(context, sourceSection, n, MissingArgumentBehaviour.UNDEFINED);

                if (ArrayUtils.contains(methodDetails.getMethodAnnotation().lowerFixnumParameters(), n)) {
                    readArgumentNode = new FixnumLowerNode(readArgumentNode);
                }

                argumentsNodes.add(readArgumentNode);
            }
        }

        if (methodDetails.getMethodAnnotation().needsBlock()) {
            argumentsNodes.add(new ReadBlockNode(context, sourceSection, UndefinedPlaceholder.INSTANCE));
        }

        final RubyNode methodNode = methodDetails.getNodeFactory().createNode(context, sourceSection, argumentsNodes.toArray(new RubyNode[argumentsNodes.size()]));
        final CheckArityNode checkArity = new CheckArityNode(context, sourceSection, arity);
        final RubyNode block = SequenceNode.sequence(context, sourceSection, checkArity, methodNode);
        final ExceptionTranslatingNode exceptionTranslatingNode = new ExceptionTranslatingNode(context, sourceSection, block);

        return new RubyRootNode(sourceSection, null, sharedMethodInfo, exceptionTranslatingNode);
    }

    public static class MethodDetails {

        private final CoreClass classAnnotation;
        private final CoreMethod methodAnnotation;
        private final NodeFactory<? extends RubyNode> nodeFactory;

        public MethodDetails(CoreClass classAnnotation, CoreMethod methodAnnotation, NodeFactory<? extends RubyNode> nodeFactory) {
            assert classAnnotation != null;
            assert methodAnnotation != null;
            assert nodeFactory != null;
            this.classAnnotation = classAnnotation;
            this.methodAnnotation = methodAnnotation;
            this.nodeFactory = nodeFactory;
        }

        public CoreClass getClassAnnotation() {
            return classAnnotation;
        }

        public CoreMethod getMethodAnnotation() {
            return methodAnnotation;
        }

        public NodeFactory<? extends RubyNode> getNodeFactory() {
            return nodeFactory;
        }

        public String getIndicativeName() {
            return classAnnotation.name() + "#" + methodAnnotation.names()[0] + "(core)";
        }
    }

}
