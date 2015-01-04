// TODO: needs copyright message from Gulfem

package org.jruby.truffle.nodes.profiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.instrument.Instrument;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.instrument.RubyWrapperNode;
import org.jruby.truffle.runtime.methods.RubyMethod;
import org.jruby.util.cli.Options;

import com.oracle.truffle.api.instrument.StandardSyntaxTag;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.nodes.Node;

public class ProfilerProber {

    private final static ProfilerProber INSTANCE = new ProfilerProber();

    private List<MethodBodyInstrument> methodBodyInstruments;
    private List<TimeProfilerInstrument> callInstruments;
    private List<ProfilerInstrument> whileInstruments;
    private List<ProfilerInstrument> iteratorLoopInstruments;
    private List<ProfilerInstrument> breakNextInstruments;
    private List<ProfilerInstrument> variableAccessInstruments;
    private List<ProfilerInstrument> operationInstruments;
    private List<ProfilerInstrument> collectionOperationInstruments;
    private Map<ProfilerInstrument, List<ProfilerInstrument>> ifInstruments;
    private List<TypeDistributionProfilerInstrument> variableAccessTypeDistributionInstruments;

    private ProfilerProber() {
        methodBodyInstruments = new ArrayList<>();
        callInstruments = new ArrayList<>();
        whileInstruments = new ArrayList<>();
        iteratorLoopInstruments = new ArrayList<>();
        breakNextInstruments = new ArrayList<>();
        variableAccessInstruments = new ArrayList<>();
        operationInstruments = new ArrayList<>();
        collectionOperationInstruments = new ArrayList<>();
        ifInstruments = new LinkedHashMap<>();
        variableAccessTypeDistributionInstruments = new ArrayList<>();
    }

    public static ProfilerProber getInstance() {
        return INSTANCE;
    }

    public Node probeAs(Node node, SyntaxTag syntaxTag, Object... objects) {
        throw new UnsupportedOperationException();
    }

    public RubyNode probeAsStatement(RubyNode node) {
        return null;
    }

    public RubyWrapperNode probeAsMethodBody(RubyNode node, RubyMethod rubyMethod) {
        RubyWrapperNode wrapper = createWrapper(node);
        MethodBodyInstrument profilerInstrument = createAttachMethodBodyInstrument(wrapper, rubyMethod);
        methodBodyInstruments.add(profilerInstrument);
        return wrapper;
    }

    public RubyWrapperNode probeAsCall(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        //wrapper.tagAs(StandardSyntaxTag.START_METHOD);
        TimeProfilerInstrument profilerInstrument = createAttachTimeProfilerInstrument(wrapper);
        callInstruments.add(profilerInstrument);
        return wrapper;
    }

    public RubyWrapperNode probeAsWhile(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        whileInstruments.add(profilerInstrument);
        return wrapper;
    }

    public RubyWrapperNode probeAsIteratorLoop(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        iteratorLoopInstruments.add(profilerInstrument);
        return wrapper;
    }

    public List<RubyWrapperNode> probeAsIf(RubyNode ifNode, RubyNode thenNode, RubyNode elseNode) {
        List<RubyWrapperNode> wrappers = new ArrayList<>();
        RubyWrapperNode ifWrapper = createWrapper(ifNode);
        RubyWrapperNode thenWrapper = createWrapper(thenNode);
        RubyWrapperNode elseWrapper = createWrapper(elseNode);
        wrappers.add(ifWrapper);
        wrappers.add(thenWrapper);
        wrappers.add(elseWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(thenWrapper);
        ProfilerInstrument elseInstrument = createAttachProfilerInstrument(elseWrapper);
        instruments.add(thenInstrument);
        instruments.add(elseInstrument);
        ifInstruments.put(ifInstrument, instruments);

        return wrappers;
    }

    public List<RubyWrapperNode> probeAsIfWithoutThen(RubyNode ifNode, RubyNode elseNode) {
        List<RubyWrapperNode> wrappers = new ArrayList<>();
        RubyWrapperNode ifWrapper = createWrapper(ifNode);
        RubyWrapperNode elseWrapper = createWrapper(elseNode);
        wrappers.add(ifWrapper);
        wrappers.add(elseWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(elseWrapper);
        instruments.add(thenInstrument);
        ifInstruments.put(ifInstrument, instruments);

        return wrappers;
    }

    public List<RubyWrapperNode> probeAsIfWithoutElse(RubyNode ifNode, RubyNode thenNode) {
        List<RubyWrapperNode> wrappers = new ArrayList<>();
        RubyWrapperNode ifWrapper = createWrapper(ifNode);
        RubyWrapperNode thenWrapper = createWrapper(thenNode);
        wrappers.add(ifWrapper);
        wrappers.add(thenWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(thenWrapper);
        instruments.add(thenInstrument);
        ifInstruments.put(ifInstrument, instruments);

        return wrappers;
    }

    public RubyWrapperNode probeAsBreakNext(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        breakNextInstruments.add(profilerInstrument);
        return wrapper;
    }

    public RubyWrapperNode probeAsVariableAccess(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        if (Options.TRUFFLE_PROFILE_TYPE_DISTRIBUTION.load()) {
            TypeDistributionProfilerInstrument profilerInstrument = createAttachProfilerTypeDistributionInstrument(wrapper);
            variableAccessTypeDistributionInstruments.add(profilerInstrument);
        } else {
            ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
            variableAccessInstruments.add(profilerInstrument);
        }

        return wrapper;
    }

    public RubyWrapperNode probeAsOperation(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        operationInstruments.add(profilerInstrument);
        return wrapper;
    }

    public RubyWrapperNode probeAsCollectionOperation(RubyNode node) {
        RubyWrapperNode wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        collectionOperationInstruments.add(profilerInstrument);
        return wrapper;
    }

    private RubyWrapperNode createWrapper(RubyNode node) {
        final RubyWrapperNode wrapper;
        if (node instanceof RubyWrapperNode) {
            wrapper = (RubyWrapperNode) node;
        } else if (node.getParent() != null && node.getParent() instanceof RubyWrapperNode) {
            wrapper = (RubyWrapperNode) node.getParent();
        } else {
            wrapper = new RubyWrapperNode(node);
        }

        return wrapper;
    }

    private static ProfilerInstrument createAttachProfilerInstrument(RubyWrapperNode wrapper) {
        final RubyNode node = wrapper.getNonWrapperNode();
        ProfilerInstrument profilerInstrument = new ProfilerInstrument(wrapper.getChild());
        //wrapper.getProbe().attach(Instrument.create(profilerInstrument, "?"));
        node.probe().attach(Instrument.create(profilerInstrument, "?"));
        return profilerInstrument;
    }

    private static TimeProfilerInstrument createAttachTimeProfilerInstrument(RubyWrapperNode wrapper) {
        final RubyNode node = wrapper.getNonWrapperNode();
        TimeProfilerInstrument profilerInstrument = new TimeProfilerInstrument(wrapper.getChild());
        // (CS) can't fix: profilerInstrument.assignSourceSection(wrapper.getChild().getSourceSection());
        //wrapper.getProbe().attach(Instrument.create(profilerInstrument, "?"));
        node.probe().attach(Instrument.create(profilerInstrument, "?"));
        return profilerInstrument;
    }

    private static TypeDistributionProfilerInstrument createAttachProfilerTypeDistributionInstrument(RubyWrapperNode wrapper) {
        final RubyNode node = wrapper.getNonWrapperNode();
        TypeDistributionProfilerInstrument profilerInstrument = new TypeDistributionProfilerInstrument(wrapper.getChild());
        //wrapper.getProbe().attach(Instrument.create(profilerInstrument, "?"));
        node.probe().attach(Instrument.create(profilerInstrument, "?"));
        return profilerInstrument;
    }

    private static MethodBodyInstrument createAttachMethodBodyInstrument(RubyWrapperNode wrapper, RubyMethod rubyMethod) {
        final RubyNode node = wrapper.getNonWrapperNode();
        MethodBodyInstrument profilerInstrument = new MethodBodyInstrument(wrapper.getChild(), rubyMethod);
        //wrapper.getProbe().attach(Instrument.create(profilerInstrument, "?"));
        node.probe().attach(Instrument.create(profilerInstrument, "?"));
        return profilerInstrument;
    }

    public List<MethodBodyInstrument> getMethodBodyInstruments() {
        return methodBodyInstruments;
    }

    public List<TimeProfilerInstrument> getCallInstruments() {
        return callInstruments;
    }

    public List<ProfilerInstrument> getWhileInstruments() {
        return whileInstruments;
    }

    public List<ProfilerInstrument> getIteratorLoopInstruments() {
        return iteratorLoopInstruments;
    }

    public List<ProfilerInstrument> getBreakNextInstruments() {
        return breakNextInstruments;
    }

    public List<ProfilerInstrument> getVariableAccessInstruments() {
        return variableAccessInstruments;
    }

    public List<ProfilerInstrument> getOperationInstruments() {
        return operationInstruments;
    }

    public List<ProfilerInstrument> getCollectionOperationInstruments() {
        return collectionOperationInstruments;
    }

    public List<TypeDistributionProfilerInstrument> getVariableAccessTypeDistributionInstruments() {
        return variableAccessTypeDistributionInstruments;
    }

    public Map<ProfilerInstrument, List<ProfilerInstrument>> getIfInstruments() {
        return ifInstruments;
    }

}
