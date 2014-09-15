/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.translator;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.frame.*;

import org.jruby.ast.*;
import org.jruby.truffle.nodes.*;
import org.jruby.truffle.nodes.cast.ArrayCastNodeFactory;
import org.jruby.truffle.nodes.cast.BooleanCastNodeFactory;
import org.jruby.truffle.nodes.control.*;
import org.jruby.truffle.nodes.control.AndNode;
import org.jruby.truffle.nodes.control.IfNode;
import org.jruby.truffle.nodes.debug.ObjectSpaceSafepointInstrument;
import org.jruby.truffle.nodes.literal.NilLiteralNode;
import org.jruby.truffle.nodes.methods.*;
import org.jruby.truffle.nodes.methods.arguments.*;
import org.jruby.truffle.nodes.methods.locals.*;
import org.jruby.truffle.nodes.profiler.ProfilerTranslator;
import org.jruby.truffle.nodes.respondto.RespondToNode;
import org.jruby.truffle.nodes.supercall.GeneralSuperCallNode;
import org.jruby.truffle.nodes.supercall.GeneralSuperReCallNode;
import org.jruby.truffle.runtime.*;
import org.jruby.truffle.runtime.methods.*;
import org.jruby.util.cli.Options;

class MethodTranslator extends BodyTranslator {

    private boolean isTopLevel;
    private boolean isBlock;

    public MethodTranslator(RubyNode currentNode, RubyContext context, BodyTranslator parent, TranslatorEnvironment environment, boolean isBlock, boolean isTopLevel, Source source) {
        super(currentNode, context, parent, environment, source);
        this.isBlock = isBlock;
        this.isTopLevel = isTopLevel;
    }

    public MethodDefinitionNode compileFunctionNode(SourceSection sourceSection, String methodName, ArgsNode argsNode, org.jruby.ast.Node bodyNode, boolean ignoreLocalVisiblity) {
        final ParameterCollector parameterCollector = new ParameterCollector();
        argsNode.accept(parameterCollector);

        for (String parameter : parameterCollector.getParameters()) {
            environment.declareVar(parameter);
        }

        final Arity arity = getArity(argsNode);

        final Arity arityForCheck;

        /*
         * If you have a block with parameters |a,| Ruby checks the arity as if was minimum 1, maximum 1. That's
         * counter-intuitive - as you'd expect the anonymous rest argument to cause it to have no maximum. Indeed,
         * that's how JRuby reports it, and by the look of their failing spec they consider this to be correct. We'll
         * follow the specs for now until we see a reason to do something else.
         */

        if (isBlock && argsNode.childNodes().size() == 2 && argsNode.getRestArgNode() instanceof org.jruby.ast.UnnamedRestArgNode) {
            arityForCheck = new Arity(arity.getMinimum(), arity.getMinimum());
        } else {
            arityForCheck = arity;
        }

        RubyNode body;

        if (bodyNode != null) {
            parentSourceSection = sourceSection;

            try {
                body = bodyNode.accept(this);
            } finally {
                parentSourceSection = null;
            }
        } else {
            body = new NilLiteralNode(context, sourceSection);
        }

        final LoadArgumentsTranslator loadArgumentsTranslator = new LoadArgumentsTranslator(currentNode, context, source, isBlock, this);
        final RubyNode loadArguments = argsNode.accept(loadArgumentsTranslator);

        final RubyNode prelude;

        if (isBlock) {
            boolean shouldSwitch = true;

            if (argsNode.getPreCount() == 0 && argsNode.getOptionalArgsCount() == 0 && argsNode.getPostCount() == 0 && argsNode.getRestArgNode() == null) {
                shouldSwitch = false;
            }

            if (argsNode.getPreCount() + argsNode.getPostCount() == 1 && argsNode.getOptionalArgsCount() == 0 && argsNode.getRestArgNode() == null) {
                shouldSwitch = false;
            }

            if (argsNode.getPreCount() == 0 && argsNode.getRestArgNode() != null) {
                shouldSwitch = false;
            }

            RubyNode preludeBuilder;

            if (shouldSwitch) {
                final RubyNode readArrayNode = new ReadPreArgumentNode(context, sourceSection, 0, MissingArgumentBehaviour.RUNTIME_ERROR);
                final RubyNode castArrayNode = ArrayCastNodeFactory.create(context, sourceSection, readArrayNode);
                final FrameSlot arraySlot = environment.declareVar(environment.allocateLocalTemp("destructure"));
                final RubyNode writeArrayNode = WriteLocalVariableNodeFactory.create(context, sourceSection, arraySlot, castArrayNode);

                final LoadArgumentsTranslator destructureArgumentsTranslator = new LoadArgumentsTranslator(currentNode, context, source, isBlock, this);
                destructureArgumentsTranslator.pushArraySlot(arraySlot);
                final RubyNode newDestructureArguments = argsNode.accept(destructureArgumentsTranslator);

                preludeBuilder = new IfNode(context, sourceSection,
                        BooleanCastNodeFactory.create(context, sourceSection,
                                new AndNode(context, sourceSection,
                                    new BehaveAsBlockNode(context, sourceSection, true),
                                    new ShouldDestructureNode(context, sourceSection, arity,
                                            new RespondToNode(context, sourceSection, readArrayNode, "to_ary")))),
                        SequenceNode.sequence(context, sourceSection, writeArrayNode, newDestructureArguments),
                        loadArguments);
            } else {
                preludeBuilder = loadArguments;
            }

            prelude = SequenceNode.sequence(context, sourceSection,
                    new IfNode(context, sourceSection,
                            BooleanCastNodeFactory.create(context, sourceSection,
                                    new BehaveAsBlockNode(context, sourceSection, true)),
                            new NilLiteralNode(context, sourceSection),
                            new CheckArityNode(context, sourceSection, arityForCheck)), preludeBuilder);
        } else {
            prelude = SequenceNode.sequence(context, sourceSection,
                    new CheckArityNode(context, sourceSection, arityForCheck),
                    loadArguments);
        }

        body = SequenceNode.sequence(context, sourceSection, prelude, body);

        if (environment.getFlipFlopStates().size() > 0) {
            body = SequenceNode.sequence(context, sourceSection, initFlipFlopStates(sourceSection), body);
        }

        if (isBlock) {
            body = new RedoableNode(context, sourceSection, body);
        }

        if (isBlock) {
            body = new CatchReturnPlaceholderNode(context, sourceSection, body, environment.getReturnID());
        } else {
            body = new CatchReturnNode(context, sourceSection, body, environment.getReturnID());
        }

        body = new CatchNextNode(context, sourceSection, body);
        body = new CatchRetryAsErrorNode(context, sourceSection, body);

        if (isBlock && isTopLevel) {
            body = new CatchBreakAsReturnNode(context, sourceSection, body);
        }

        body = context.getASTProber().probeAsPeriodic(body);

        /**
         * Profile collection loops such as each, step, collect, loop, etc.
         * TODO
         * It's better to do this transformation in the profiler translation step,
         * but currently information is only available here.
         */

        if (isBlock && Options.TRUFFLE_PROFILE_CONTROL_FLOW.load()) {
            body = ProfilerTranslator.getInstance().getProfilerProber().probeAsIteratorLoop(body);
        }

        if (!isBlock) {
            body = new ExceptionTranslatingNode(context, sourceSection, body);
        }

        final RubyRootNode rootNode = new RubyRootNode(sourceSection, environment.getFrameDescriptor(), environment.getSharedMethodInfo(), body);

        if (isBlock) {
            final CallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
            final CallTarget callTargetForMethods = withoutBlockDestructureSemantics(callTarget);

            /**
             * Profile the nodes inside block
             */
            if (Options.TRUFFLE_PROFILE_CALLS.load() || Options.TRUFFLE_PROFILE_CONTROL_FLOW.load() || Options.TRUFFLE_PROFILE_VARIABLE_ACCESSES.load()
            || Options.TRUFFLE_PROFILE_OPERATIONS.load() || Options.TRUFFLE_PROFILE_COLLECTION_OPERATIONS.load()) {
                ProfilerTranslator.getInstance().translate(rootNode, false, true, null);
            }
            return new BlockDefinitionNode(context, sourceSection, methodName, environment.getSharedMethodInfo(), environment.needsDeclarationFrame(), callTarget, callTargetForMethods, rootNode);
        } else {
            return new MethodDefinitionNode(context, sourceSection, methodName, environment.getSharedMethodInfo(), environment.needsDeclarationFrame(), rootNode, ignoreLocalVisiblity);
        }
    }

    private CallTarget withoutBlockDestructureSemantics(CallTarget callTarget) {
        if (callTarget instanceof RootCallTarget && ((RootCallTarget) callTarget).getRootNode() instanceof RubyRootNode) {
            final RubyRootNode newRootNode = ((RubyRootNode) ((RootCallTarget) callTarget).getRootNode()).cloneRubyRootNode();

            for (BehaveAsBlockNode behaveAsBlockNode : NodeUtil.findAllNodeInstances(newRootNode, BehaveAsBlockNode.class)) {
                behaveAsBlockNode.setBehaveAsBlock(false);
            }

            final RubyRootNode newRootNodeWithCatchReturn = new RubyRootNode(newRootNode.getSourceSection(),
                    newRootNode.getFrameDescriptor(), newRootNode.getSharedMethodInfo(),
                        new CatchReturnNode(context, newRootNode.getSourceSection(), newRootNode.getBody(), getEnvironment().getReturnID()));

            return Truffle.getRuntime().createCallTarget(newRootNodeWithCatchReturn);
        } else {
            throw new UnsupportedOperationException("Can't change the semantics of an opaque call target");
        }
    }

    private static Arity getArity(org.jruby.ast.ArgsNode argsNode) {
        final int minimum = argsNode.getRequiredArgsCount();
        final int maximum = argsNode.getMaxArgumentsCount();
        return new Arity(minimum, maximum == -1 ? Arity.NO_MAXIMUM : maximum);
    }

    @Override
    public RubyNode visitSuperNode(org.jruby.ast.SuperNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        final ArgumentsAndBlockTranslation argumentsAndBlock = translateArgumentsAndBlock(sourceSection, node.getIterNode(), node.getArgsNode(), null, environment.getNamedMethodName());

        return new GeneralSuperCallNode(context, sourceSection, environment.getNamedMethodName(), argumentsAndBlock.getBlock(), argumentsAndBlock.getArguments(), argumentsAndBlock.isSplatted());
    }

    @Override
    public RubyNode visitZSuperNode(org.jruby.ast.ZSuperNode node) {
        final SourceSection sourceSection = translate(node.getPosition());

        return new GeneralSuperReCallNode(context, sourceSection, environment.getNamedMethodName());
    }

    @Override
    protected FlipFlopStateNode createFlipFlopState(SourceSection sourceSection, int depth) {
        if (isBlock) {
            environment.setNeedsDeclarationFrame();
            return parent.createFlipFlopState(sourceSection, depth + 1);
        } else {
            return super.createFlipFlopState(sourceSection, depth);
        }
    }

}
