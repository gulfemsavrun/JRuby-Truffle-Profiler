package org.jruby.truffle.nodes.profiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.debug.RubyNodeProber;
import org.jruby.truffle.nodes.debug.RubyWrapper;
import org.jruby.truffle.runtime.methods.RubyMethod;
import org.jruby.util.cli.Options;

import com.oracle.truffle.api.instrument.StandardSyntaxTag;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.nodes.Node;

public class ProfilerProber implements RubyNodeProber {

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

    public ProfilerProber() {
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
    
    @Override
    public Node probeAs(Node node, SyntaxTag syntaxTag, Object... objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RubyNode probeAsStatement(RubyNode node) {
        return null;
    }

    public RubyWrapper probeAsMethodBody(RubyNode node, RubyMethod rubyMethod) {
        RubyWrapper wrapper = createWrapper(node);
        MethodBodyInstrument profilerInstrument = createAttachMethodBodyInstrument(wrapper, rubyMethod);
        methodBodyInstruments.add(profilerInstrument);
        return wrapper;
    }
    
    public RubyWrapper probeAsCall(RubyNode node) {
    	RubyWrapper wrapper = createWrapper(node);
    	wrapper.tagAs(StandardSyntaxTag.START_METHOD);
        TimeProfilerInstrument profilerInstrument = createAttachTimeProfilerInstrument(wrapper);
        callInstruments.add(profilerInstrument);
        return wrapper;
    }
    
    public RubyWrapper probeAsWhile(RubyNode node) {
    	RubyWrapper wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        whileInstruments.add(profilerInstrument);
        return wrapper;
    }
    
    public RubyWrapper probeAsIteratorLoop(RubyNode node) {
    	RubyWrapper wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        iteratorLoopInstruments.add(profilerInstrument);
        return wrapper;
    }
    
    public List<RubyWrapper> probeAsIf(RubyNode ifNode, RubyNode thenNode, RubyNode elseNode) {
        List<RubyWrapper> wrappers = new ArrayList<>();
        RubyWrapper ifWrapper = createWrapper(ifNode);
        RubyWrapper thenWrapper = createWrapper(thenNode);
        RubyWrapper elseWrapper = createWrapper(elseNode);
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

    public List<RubyWrapper> probeAsIfWithoutThen(RubyNode ifNode, RubyNode elseNode) {
        List<RubyWrapper> wrappers = new ArrayList<>();
        RubyWrapper ifWrapper = createWrapper(ifNode);
        RubyWrapper elseWrapper = createWrapper(elseNode);
        wrappers.add(ifWrapper);
        wrappers.add(elseWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(elseWrapper);
        instruments.add(thenInstrument);
        ifInstruments.put(ifInstrument, instruments);

        return wrappers;
    }

    public List<RubyWrapper> probeAsIfWithoutElse(RubyNode ifNode, RubyNode thenNode) {
        List<RubyWrapper> wrappers = new ArrayList<>();
        RubyWrapper ifWrapper = createWrapper(ifNode);
        RubyWrapper thenWrapper = createWrapper(thenNode);
        wrappers.add(ifWrapper);
        wrappers.add(thenWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(thenWrapper);
        instruments.add(thenInstrument);
        ifInstruments.put(ifInstrument, instruments);

        return wrappers;
    }

    public RubyWrapper probeAsBreakNext(RubyNode node) {
        RubyWrapper wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        breakNextInstruments.add(profilerInstrument);
        return wrapper;
    }
        
    public RubyWrapper probeAsVariableAccess(RubyNode node) {
        RubyWrapper wrapper = createWrapper(node);
        if (Options.TRUFFLE_PROFILE_TYPE_DISTRIBUTION.load()) {
            TypeDistributionProfilerInstrument profilerInstrument = createAttachProfilerTypeDistributionInstrument(wrapper);
            variableAccessTypeDistributionInstruments.add(profilerInstrument);
        } else {
            ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
            variableAccessInstruments.add(profilerInstrument); 
        }

        return wrapper;
    }
    
    public RubyWrapper probeAsOperation(RubyNode node) {
        RubyWrapper wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        operationInstruments.add(profilerInstrument);
        return wrapper;
    }
    
    public RubyWrapper probeAsCollectionOperation(RubyNode node) {
        RubyWrapper wrapper = createWrapper(node);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        collectionOperationInstruments.add(profilerInstrument);
        return wrapper;
    }
    
    private RubyWrapper createWrapper(RubyNode node) {
    	final RubyWrapper wrapper;
        if (node instanceof RubyWrapper) {
        	wrapper = (RubyWrapper) node;
        } else if (node.getParent() != null && node.getParent() instanceof RubyWrapper) {
        	wrapper = (RubyWrapper) node.getParent();
        } else {
            wrapper = new RubyWrapper(node.getContext(), node.getEncapsulatingSourceSection(), node);
        }

        return wrapper;
    }

    private static ProfilerInstrument createAttachProfilerInstrument(RubyWrapper wrapper) {
        ProfilerInstrument profilerInstrument = new ProfilerInstrument(wrapper.getChild());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    private static TimeProfilerInstrument createAttachTimeProfilerInstrument(RubyWrapper wrapper) {
        TimeProfilerInstrument profilerInstrument = new TimeProfilerInstrument(wrapper.getChild());
        profilerInstrument.assignSourceSection(wrapper.getChild().getSourceSection());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    private static TypeDistributionProfilerInstrument createAttachProfilerTypeDistributionInstrument(RubyWrapper wrapper) {
        TypeDistributionProfilerInstrument profilerInstrument = new TypeDistributionProfilerInstrument(wrapper.getChild());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    private static MethodBodyInstrument createAttachMethodBodyInstrument(RubyWrapper wrapper, RubyMethod rubyMethod) {
        MethodBodyInstrument profilerInstrument = new MethodBodyInstrument(wrapper.getChild(), rubyMethod);
        wrapper.getProbe().addInstrument(profilerInstrument);
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
    
	@Override
	public RubyNode probeAsPeriodic(RubyNode node) {
		// TODO Auto-generated method stub
		return null;
	}
    
}
